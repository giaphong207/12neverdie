package com.auction.server.service;

import com.auction.server.concurrency.AuctionLockManager;
import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.ItemDao;
import com.auction.server.dao.UserDao;
import com.auction.server.realtime.AuctionEnricher;
import com.auction.server.realtime.AuctionSubscriptionManager;
import com.auction.server.realtime.EventBroadcaster;
import com.auction.shared.exception.AppExceptions.AuctionNotFoundException;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.user.Role;
import com.auction.shared.model.user.User;
import com.auction.shared.networkMessage.AuctionEvents.AuctionCancelledEvent;
import com.auction.shared.networkMessage.AuctionEvents.AuctionEvent;
import com.auction.shared.networkMessage.AuctionEvents.AuctionUpdatedEvent;
import com.auction.support.TestDataFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
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

    // ════════════════════════════════════════════════════════════
    // syncByTime — chuyển trạng thái theo thời gian
    // ════════════════════════════════════════════════════════════

    @Test
    @DisplayName("syncByTime: OPEN + đã đến startTime → RUNNING + broadcast")
    void sync_open_past_start_transitions_to_running() {
        Auction auction = openAuctionStartedAlready();
        auctionDao.save(auction);

        Auction result = lifecycleService.syncByTime(auction.getId());

        assertEquals(AuctionStatus.RUNNING, result.getStatus());
        assertEquals(1, broadcaster.countOf(AuctionUpdatedEvent.class));
    }

    @Test
    @DisplayName("syncByTime: OPEN + chưa đến startTime → vẫn OPEN, không broadcast")
    void sync_open_before_start_stays_open() {
        Auction auction = openAuctionInFuture();
        auctionDao.save(auction);

        Auction result = lifecycleService.syncByTime(auction.getId());

        assertEquals(AuctionStatus.OPEN, result.getStatus());
        assertEquals(0, broadcaster.totalCount());
    }

    @Test
    @DisplayName("syncByTime: RUNNING + quá endTime → FINISHED + broadcast")
    void sync_running_past_end_transitions_to_finished() {
        Auction auction = runningAuctionExpired();
        auctionDao.save(auction);

        Auction result = lifecycleService.syncByTime(auction.getId());

        assertEquals(AuctionStatus.FINISHED, result.getStatus());
        assertTrue(broadcaster.countOf(AuctionUpdatedEvent.class) >= 1);
    }

    @Test
    @DisplayName("syncByTime: RUNNING + còn thời gian → vẫn RUNNING")
    void sync_running_with_time_stays_running() {
        Auction auction = TestDataFactory.auctionWithPlentyOfTime();
        auctionDao.save(auction);

        Auction result = lifecycleService.syncByTime(auction.getId());

        assertEquals(AuctionStatus.RUNNING, result.getStatus());
        assertEquals(0, broadcaster.totalCount());
    }

    @Test
    @DisplayName("syncByTime: chain OPEN→RUNNING→FINISHED nếu cả startTime và endTime đều quá")
    void sync_open_past_both_times_chains_to_finished() {
        LocalDateTime past = LocalDateTime.now().minusHours(2);
        Auction auction = new Auction(
                UUID.randomUUID().toString(),
                "item-x", "seller-x",
                5_000_000L, 100_000L,
                AuctionStatus.OPEN,
                past,
                past.plusMinutes(30)
        );
        auctionDao.save(auction);

        Auction result = lifecycleService.syncByTime(auction.getId());

        assertEquals(AuctionStatus.FINISHED, result.getStatus());
    }

    @Test
    @DisplayName("syncByTime: auction không tồn tại → AuctionNotFoundException")
    void sync_non_existent_throws() {
        assertThrows(AuctionNotFoundException.class,
                () -> lifecycleService.syncByTime("ghost-auction-id"));
    }

    @Test
    @DisplayName("syncByTime: terminal status (FINISHED/PAID/CANCELED) — idempotent")
    void sync_terminal_state_does_nothing() {
        Auction auction = TestDataFactory.finishedAuction();
        auctionDao.save(auction);

        Auction result = lifecycleService.syncByTime(auction.getId());

        assertEquals(AuctionStatus.FINISHED, result.getStatus());
        assertEquals(0, broadcaster.totalCount());
    }

    // ════════════════════════════════════════════════════════════
    // cancelAuction
    // ════════════════════════════════════════════════════════════

    @Test
    @DisplayName("cancelAuction: OPEN → CANCELED + broadcast")
    void cancel_open_auction() {
        Auction auction = openAuctionInFuture();
        auctionDao.save(auction);

        Auction result = lifecycleService.cancelAuction(auction.getId(), Role.SELLER);

        assertEquals(AuctionStatus.CANCELED, result.getStatus());
        assertEquals(1, broadcaster.countOf(AuctionCancelledEvent.class));
    }

    @Test
    @DisplayName("cancelAuction: RUNNING → CANCELED + broadcast (Admin override)")
    void cancel_running_auction() {
        Auction auction = TestDataFactory.runningAuction(5_000_000L, 100_000L, 300);
        auctionDao.save(auction);

        Auction result = lifecycleService.cancelAuction(auction.getId(), Role.ADMIN);

        assertEquals(AuctionStatus.CANCELED, result.getStatus());
        assertEquals(1, broadcaster.countOf(AuctionCancelledEvent.class));
    }

    @Test
    @DisplayName("cancelAuction: FINISHED → IllegalStateException (không cho cancel)")
    void cancel_finished_auction_throws() {
        Auction auction = TestDataFactory.finishedAuction();
        auctionDao.save(auction);

        assertThrows(IllegalStateException.class,
                () -> lifecycleService.cancelAuction(auction.getId(), Role.ADMIN));
    }

    @Test
    @DisplayName("cancelAuction: không tồn tại → AuctionNotFoundException")
    void cancel_non_existent_throws() {
        assertThrows(AuctionNotFoundException.class,
                () -> lifecycleService.cancelAuction("ghost", Role.ADMIN));
    }

    @Test
    @DisplayName("shutdown không ném exception, scheduler ngưng nhận task")
    void shutdown_does_not_throw() {
        assertDoesNotThrow(() -> lifecycleService.shutdown());
        assertDoesNotThrow(() -> lifecycleService.shutdown());
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════

    private Auction openAuctionInFuture() {
        LocalDateTime future = LocalDateTime.now().plusHours(1);
        return new Auction(
                UUID.randomUUID().toString(), "item-1", "seller-1",
                5_000_000L, 100_000L,
                AuctionStatus.OPEN,
                future, future.plusMinutes(30)
        );
    }

    private Auction openAuctionStartedAlready() {
        LocalDateTime now = LocalDateTime.now();
        return new Auction(
                UUID.randomUUID().toString(), "item-1", "seller-1",
                5_000_000L, 100_000L,
                AuctionStatus.OPEN,
                now.minusMinutes(1),
                now.plusHours(1)
        );
    }

    private Auction runningAuctionExpired() {
        LocalDateTime past = LocalDateTime.now().minusMinutes(5);
        return new Auction(
                UUID.randomUUID().toString(), "item-1", "seller-1",
                5_000_000L, 100_000L,
                AuctionStatus.RUNNING,
                past.minusHours(1),
                past
        );
    }

    // ════════════════════════════════════════════════════════════
    // FAKES
    // ════════════════════════════════════════════════════════════

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

    static class FakeBroadcaster extends EventBroadcaster {
        private final List<AuctionEvent> events = new CopyOnWriteArrayList<>();

        FakeBroadcaster() {
            super(new AuctionSubscriptionManager(),
                  new AuctionEnricher(new NoOpItemDao(), new NoOpUserDao()));
        }

        @Override
        public void broadcast(AuctionEvent event) {
            events.add(event);
        }

        int countOf(Class<? extends AuctionEvent> type) {
            return (int) events.stream().filter(type::isInstance).count();
        }

        int totalCount() { return events.size(); }
    }

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
