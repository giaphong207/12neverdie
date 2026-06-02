package com.auction.server.service;

import com.auction.server.concurrency.AuctionLockManager;
import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.BidDao;
import com.auction.server.dao.Database;
import com.auction.server.dao.UserDao;
import com.auction.shared.exception.AppExceptions.AuctionClosedException;
import com.auction.shared.exception.AppExceptions.InvalidBidException;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;
import com.auction.shared.model.bid.Bid;
import com.auction.shared.model.user.Bidder;
import com.auction.shared.model.user.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("auctionId null → InvalidBidException")
        void null_auction_id_throws() {
            assertThrows(InvalidBidException.class,
                    () -> bidService.placeBid(null, BIDDER_ID, 5_100_000L));
        }

        @Test
        @DisplayName("auctionId blank → InvalidBidException")
        void blank_auction_id_throws() {
            assertThrows(InvalidBidException.class,
                    () -> bidService.placeBid("  ", BIDDER_ID, 5_100_000L));
        }

        @Test
        @DisplayName("bidderId null → InvalidBidException")
        void null_bidder_id_throws() {
            assertThrows(InvalidBidException.class,
                    () -> bidService.placeBid(AUCTION_ID, null, 5_100_000L));
        }

        @Test
        @DisplayName("amount = 0 → InvalidBidException")
        void zero_amount_throws() {
            assertThrows(InvalidBidException.class,
                    () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, 0L));
        }

        @Test
        @DisplayName("amount âm → InvalidBidException")
        void negative_amount_throws() {
            assertThrows(InvalidBidException.class,
                    () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, -1000L));
        }
    }

    @Nested
    @DisplayName("Auction state")
    class AuctionStateChecks {

        @Test
        @DisplayName("Auction FINISHED → AuctionClosedException")
        void finished_auction_rejects_bid() {
            auction.finish();
            auctionDao.save(auction);

            assertThrows(AuctionClosedException.class,
                    () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, 5_100_000L));
        }

        @Test
        @DisplayName("Auction CANCELED → AuctionClosedException")
        void canceled_auction_rejects_bid() {
            auction.cancel();
            auctionDao.save(auction);

            assertThrows(AuctionClosedException.class,
                    () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, 5_100_000L));
        }
    }

    @Nested
    @DisplayName("Amount validation")
    class AmountValidation {

        @Test
        @DisplayName("Bid bằng currentPrice → InvalidBidException")
        void bid_equal_current_price_throws() {
            assertThrows(InvalidBidException.class,
                    () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, 5_000_000L));
        }

        @Test
        @DisplayName("Bid < currentPrice + minIncrement → InvalidBidException")
        void bid_below_min_increment_throws() {
            assertThrows(InvalidBidException.class,
                    () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, 5_050_000L));
        }

        @Test
        @DisplayName("Bid = currentPrice + minIncrement → CHẤP NHẬN")
        void bid_exactly_min_increment_accepted() {
            BidOutcome outcome = bidService.placeBid(AUCTION_ID, BIDDER_ID, 5_100_000L);
            assertNotNull(outcome);
            assertEquals(5_100_000L, outcome.bid().getAmount());
        }
    }

    @Nested
    @DisplayName("Business rules")
    class BusinessRules {

        @Test
        @DisplayName("Seller tự bid auction mình → InvalidBidException")
        void seller_self_bid_throws() {
            userDao.save(new Bidder(SELLER_ID, "seller-as-bidder", "pwd", 100_000_000L));
            assertThrows(InvalidBidException.class,
                    () -> bidService.placeBid(AUCTION_ID, SELLER_ID, 5_100_000L));
        }

        @Test
        @DisplayName("Bidder đang là leader → InvalidBidException")
        void leader_cannot_re_bid_throws() {
            bidService.placeBid(AUCTION_ID, BIDDER_ID, 5_100_000L);
            assertThrows(InvalidBidException.class,
                    () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, 5_300_000L));
        }

        @Test
        @DisplayName("Ví không đủ tiền → InvalidBidException")
        void insufficient_balance_throws() {
            userDao.save(new Bidder("broke-bidder", "broke", "pwd", 1_000L));
            assertThrows(InvalidBidException.class,
                    () -> bidService.placeBid(AUCTION_ID, "broke-bidder", 5_100_000L));
        }

        @Test
        @DisplayName("Bidder không tồn tại trong DB → InvalidBidException")
        void unknown_bidder_throws() {
            assertThrows(InvalidBidException.class,
                    () -> bidService.placeBid(AUCTION_ID, "ghost-bidder", 5_100_000L));
        }
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
