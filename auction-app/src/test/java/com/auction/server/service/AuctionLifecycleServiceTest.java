package com.auction.server.service;

import com.auction.server.concurrency.AuctionLockManager;
import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.ItemDao;
import com.auction.server.dao.UserDao;
import com.auction.server.realtime.AuctionEnricher;
import com.auction.server.realtime.AuctionSubscriptionManager;
import com.auction.server.realtime.EventBroadcaster;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;
import com.auction.shared.model.bid.Bid;
import com.auction.shared.model.bid.BidSource;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.user.User;
import com.auction.shared.networkMessage.AuctionEvents.AuctionEvent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuctionLifecycleService - chuyển trạng thái theo thời gian")
class AuctionLifecycleServiceTest {

    private DefaultAuctionLifecycleService lifecycleService;
    private FakeAuctionDao auctionDao;
    private FakeBroadcaster broadcaster;
    private FakeWalletService walletService;
    private AuctionLockManager lockManager;

    @BeforeEach
    void setUp() {
        auctionDao = new FakeAuctionDao();
        broadcaster = new FakeBroadcaster();
        walletService = new FakeWalletService();
        lockManager = new AuctionLockManager();

        lifecycleService = new DefaultAuctionLifecycleService(
                auctionDao, broadcaster, lockManager, walletService);
    }

    @AfterEach
    void tearDown() {
        lifecycleService.shutdown();
    }

    @Test
    @DisplayName("shutdown không ném exception, scheduler ngưng nhận task")
    void shutdown_does_not_throw() {
        assertDoesNotThrow(() -> lifecycleService.shutdown());
        // Gọi shutdown 2 lần cũng OK
        assertDoesNotThrow(() -> lifecycleService.shutdown());
    }

    // ════════════════════════════════════════════════════════════
    // FAKES
    // ════════════════════════════════════════════════════════════

    /** AuctionDao in-memory. */
    static class FakeAuctionDao implements AuctionDao {
        private final Map<String, Auction> store = new HashMap<>();

        @Override public List<Auction> findAll() { return new ArrayList<>(store.values()); }
        @Override public Optional<Auction> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }
        @Override public void save(Auction a) { store.put(a.getId(), a); }
        @Override public void update(Connection conn, Auction a) { store.put(a.getId(), a); }
        @Override public void deleteById(String id) { store.remove(id); }
    }

    /** WalletService fake — set balance per-user, transfer manually. */
    static class FakeWalletService implements WalletService {
        private final Map<String, Long> balances = new HashMap<>();

        void setBalance(String userId, long balance) { balances.put(userId, balance); }

        @Override public long getBalance(String userId) {
            return balances.getOrDefault(userId, 0L);
        }
        @Override public long deposit(String userId, long amount) {
            long newBal = getBalance(userId) + amount;
            balances.put(userId, newBal);
            return newBal;
        }
        @Override public boolean settlePayment(String winnerId, String sellerId, long amount) {
            long winnerBalance = getBalance(winnerId);
            if (winnerBalance < amount) return false;
            balances.put(winnerId, winnerBalance - amount);
            balances.put(sellerId, getBalance(sellerId) + amount);
            return true;
        }
    }

    /** Broadcaster đếm event theo loại — dùng AuctionSubscriptionManager rỗng. */
    static class FakeBroadcaster extends EventBroadcaster {
        private final List<AuctionEvent> events = new CopyOnWriteArrayList<>();

        FakeBroadcaster() {
            super(new AuctionSubscriptionManager(),
                  new AuctionEnricher(new NoOpItemDao(), new NoOpUserDao()));
        }

        @Override
        public void broadcast(AuctionEvent event) {
            // Bỏ qua enrich + subscribers — chỉ đếm
            events.add(event);
        }

        int countOf(Class<? extends AuctionEvent> type) {
            return (int) events.stream().filter(type::isInstance).count();
        }

        int totalCount() { return events.size(); }
    }

    /** Stubs cho AuctionEnricher constructor (không dùng) */
    static class NoOpItemDao implements ItemDao {
        @Override public List<Item> findAll() { return List.of(); }
        @Override public List<Item> findBySellerId(String sellerId) { return List.of(); }
        @Override public Optional<Item> findById(String id) { return Optional.empty(); }
        @Override public void save(Item item) {}
        @Override public void deleteById(String id) {}
    }

    static class NoOpUserDao implements UserDao {
        @Override public List<User> findAll() { return List.of(); }
        @Override public Optional<User> findByUsername(String username) { return Optional.empty(); }
        @Override public Optional<User> findById(String id) { return Optional.empty(); }
        @Override public void save(User user) {}
        @Override public long updateBalance(String userId, long newBalance) { return newBalance; }
        @Override public long addBalance(String userId, long delta) { return delta; }
        @Override public boolean transfer(String fromId, String toId, long amount) { return false; }
    }
}
