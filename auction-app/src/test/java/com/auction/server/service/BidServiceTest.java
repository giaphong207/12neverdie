package com.auction.server.service;

import com.auction.server.concurrency.AuctionLockManager;
import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.BidDao;
import com.auction.server.dao.Database;
import com.auction.server.dao.UserDao;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;
import com.auction.shared.model.bid.Bid;
import com.auction.shared.model.user.Bidder;
import com.auction.shared.model.user.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BidService - placeBid (trái tim hệ thống)")
class BidServiceTest {

    private static final String AUCTION_ID = "auction-test-1";
    private static final String SELLER_ID = "seller-1";
    private static final String BIDDER_ID = "bidder-1";

    private Database db;
    private FakeAuctionDao auctionDao;
    private FakeBidDao bidDao;
    private FakeUserDao userDao;
    private FakeLifecycleService lifecycleService;
    private AuctionLockManager lockManager;
    private AntiSnipingService antiSnipingService;
    private FakeAutoBidService autoBidService;

    private DefaultBidService bidService;
    private Auction auction;

    @BeforeEach
    void setUp() {
        db = Database.getInstance();

        auctionDao = new FakeAuctionDao();
        bidDao = new FakeBidDao();
        userDao = new FakeUserDao();
        lifecycleService = new FakeLifecycleService(auctionDao);
        lockManager = new AuctionLockManager();
        antiSnipingService = new DefaultAntiSnipingService(
                Duration.ofSeconds(60), Duration.ofSeconds(60));
        autoBidService = new FakeAutoBidService();

        bidService = new DefaultBidService(
                db, auctionDao, bidDao, userDao,
                lifecycleService, lockManager,
                antiSnipingService, autoBidService);

        // Auction RUNNING, currentPrice 5tr, minIncrement 100k, còn 300s
        auction = new Auction(
                AUCTION_ID, "item-1", SELLER_ID,
                5_000_000L, 100_000L,
                AuctionStatus.RUNNING,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusSeconds(300)
        );
        auctionDao.save(auction);

        userDao.save(new Bidder(BIDDER_ID, "bidder1", "pwd", 10_000_000L));
    }

    // ════════════════════════════════════════════════════════════
    // FAKES
    // ════════════════════════════════════════════════════════════

    static class FakeAuctionDao implements AuctionDao {
        private final Map<String, Auction> store = new ConcurrentHashMap<>();

        @Override public List<Auction> findAll() { return new ArrayList<>(store.values()); }
        @Override public Optional<Auction> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }
        @Override public void save(Auction a) { store.put(a.getId(), a); }
        @Override public void update(Connection conn, Auction a) { store.put(a.getId(), a); }
        @Override public void deleteById(String id) { store.remove(id); }
    }

    static class FakeBidDao implements BidDao {
        private final List<Bid> saved = Collections.synchronizedList(new ArrayList<>());

        @Override public List<Bid> findByAuctionId(String auctionId) {
            return saved.stream()
                    .filter(b -> b.getAuctionId().equals(auctionId))
                    .toList();
        }
        @Override public void save(Bid bid) { saved.add(bid); }
        @Override public void save(Connection conn, Bid bid) { saved.add(bid); }
        @Override public int countByAuctionId(String auctionId) {
            return (int) saved.stream()
                    .filter(b -> b.getAuctionId().equals(auctionId))
                    .count();
        }
        int savedCount() { return saved.size(); }
        int rollbackCount() { return 0; }
    }

    static class FakeUserDao implements UserDao {
        private final Map<String, User> store = new ConcurrentHashMap<>();
        @Override public List<User> findAll() { return new ArrayList<>(store.values()); }
        @Override public Optional<User> findByUsername(String username) {
            return store.values().stream()
                    .filter(u -> u.getUsername().equals(username))
                    .findFirst();
        }
        @Override public Optional<User> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }
        @Override public void save(User user) { store.put(user.getId(), user); }
        @Override public long updateBalance(String userId, long newBalance) {
            User u = store.get(userId);
            if (u != null) u.setBalance(newBalance);
            return newBalance;
        }
        @Override public synchronized long addBalance(String userId, long delta) {
            User u = store.get(userId);
            if (u == null) return 0;
            u.setBalance(u.getBalance() + delta);
            return u.getBalance();
        }
        @Override public synchronized boolean transfer(String fromId, String toId, long amount) {
            User from = store.get(fromId); User to = store.get(toId);
            if (from == null || to == null) return false;
            if (from.getBalance() < amount) return false;
            from.setBalance(from.getBalance() - amount);
            to.setBalance(to.getBalance() + amount);
            return true;
        }
    }

    static class FakeLifecycleService implements AuctionLifecycleService {
        private final FakeAuctionDao dao;
        private final AtomicInteger rescheduleCount = new AtomicInteger();

        FakeLifecycleService(FakeAuctionDao dao) { this.dao = dao; }

        @Override public Auction syncByTime(String auctionId) {
            return dao.findById(auctionId).orElseThrow(
                    () -> new com.auction.shared.exception.AppExceptions.AuctionNotFoundException(auctionId));
        }
        @Override public Auction cancelAuction(String auctionId,
                                                com.auction.shared.model.user.Role role) { return null; }
        @Override public void scheduleStart(Auction auction) {}
        @Override public void scheduleClose(Auction auction) {}
        @Override public void rescheduleClose(Auction auction) {
            rescheduleCount.incrementAndGet();
        }
        @Override public void schedulePaymentTimeout(Auction auction) {}
        @Override public void shutdown() {}

        int rescheduleCount() { return rescheduleCount.get(); }
    }

    static class FakeAutoBidService implements AutoBidService {
        private final AtomicInteger resolveCount = new AtomicInteger();
        private java.util.function.Function<Auction, Boolean> cascadeBehavior = a -> false;

        void setCascadeBehavior(java.util.function.Function<Auction, Boolean> behavior) {
            this.cascadeBehavior = behavior;
        }
        int resolveCount() { return resolveCount.get(); }

        @Override public void upsertConfig(String auctionId, String bidderId,
                                            long maxAmount, long increment) {}
        @Override public java.util.List<com.auction.shared.model.bid.AutoBidConfig>
                        getConfigsByAuction(String auctionId) { return java.util.List.of(); }
        @Override public boolean disableConfig(String auctionId, String bidderId) { return false; }
        @Override public boolean resolveAutoBids(Auction auction) {
            resolveCount.incrementAndGet();
            return cascadeBehavior.apply(auction);
        }
    }
}
