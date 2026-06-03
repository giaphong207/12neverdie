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
import com.auction.shared.model.bid.BidSource;
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
import java.util.concurrent.*;
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

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("Bid hợp lệ: cập nhật currentPrice + highestBidder + persist + return BidOutcome")
        void valid_bid_updates_state() {
            BidOutcome outcome = bidService.placeBid(AUCTION_ID, BIDDER_ID, 5_500_000L);

            assertEquals(5_500_000L, auction.getCurrentPrice());
            assertEquals(BIDDER_ID, auction.getHighestBidderId());
            assertEquals(1, auction.getBidHistory().size());

            assertNotNull(outcome.bid());
            assertEquals(BIDDER_ID, outcome.bid().getBidderId());
            assertEquals(5_500_000L, outcome.bid().getAmount());
            assertEquals(BidSource.MANUAL, outcome.bid().getSource());

            assertEquals(1, bidDao.savedCount());
            assertEquals(0, bidDao.rollbackCount());
        }

        @Test
        @DisplayName("Bid không trong cửa sổ anti-sniping → extendedSeconds = 0, không reschedule")
        void valid_bid_no_anti_sniping() {
            BidOutcome outcome = bidService.placeBid(AUCTION_ID, BIDDER_ID, 5_500_000L);

            assertEquals(0L, outcome.extendedSeconds());
            assertEquals(0, lifecycleService.rescheduleCount());
        }
    }

    @Nested
    @DisplayName("Anti-sniping integration")
    class AntiSnipingIntegration {

        @Test
        @DisplayName("Bid trong 60s cuối → endTime tăng 60s + reschedule + extendedSeconds = 60")
        void bid_in_last_minute_extends_end_time() {
            Auction late = new Auction(
                    "auction-late", "item-2", "other-seller",
                    5_000_000L, 100_000L,
                    AuctionStatus.RUNNING,
                    LocalDateTime.now().minusMinutes(5),
                    LocalDateTime.now().plusSeconds(20)
            );
            auctionDao.save(late);
            LocalDateTime endBefore = late.getEndTime();

            BidOutcome outcome = bidService.placeBid(
                    "auction-late", BIDDER_ID, 5_100_000L);

            Duration delta = Duration.between(endBefore, late.getEndTime());
            assertEquals(60L, delta.toSeconds());

            assertEquals(60L, outcome.extendedSeconds());
            assertEquals(1, lifecycleService.rescheduleCount());
        }
    }

    @Nested
    @DisplayName("Auto-bid cascade integration")
    class AutoBidIntegration {

        @Test
        @DisplayName("Sau khi manual bid → autoBidService.resolveAutoBids được gọi 1 lần")
        void resolve_auto_bids_called_once() {
            bidService.placeBid(AUCTION_ID, BIDDER_ID, 5_100_000L);

            assertEquals(1, autoBidService.resolveCount());
        }

        @Test
        @DisplayName("AutoBid cascade thêm bid → toàn bộ được persist trong cùng transaction")
        void cascade_bids_persisted_in_same_transaction() {
            autoBidService.setCascadeBehavior((a) -> {
                a.addBid(Bid.createNew(a.getId(), "auto-1", 5_200_000L, BidSource.AUTO));
                a.addBid(Bid.createNew(a.getId(), "auto-2", 5_400_000L, BidSource.AUTO));
                return true;
            });

            bidService.placeBid(AUCTION_ID, BIDDER_ID, 5_100_000L);

            assertEquals(3, bidDao.savedCount());
            assertEquals(0, bidDao.rollbackCount());
        }
    }

    @Nested
    @DisplayName("triggerAutoBids — đặt giá mở màn khi vừa cài auto-bid (PR #54)")
    class TriggerAutoBidsOpeningBid {

        @Test
        @DisplayName("auctionId null → Optional.empty, KHÔNG chạy cascade")
        void null_auction_id_returns_empty() {
            Optional<BidOutcome> result = bidService.triggerAutoBids(null);

            assertTrue(result.isEmpty());
            assertEquals(0, autoBidService.resolveCount());
        }

        @Test
        @DisplayName("auctionId blank → Optional.empty, KHÔNG chạy cascade")
        void blank_auction_id_returns_empty() {
            Optional<BidOutcome> result = bidService.triggerAutoBids("   ");

            assertTrue(result.isEmpty());
            assertEquals(0, autoBidService.resolveCount());
        }

        @Test
        @DisplayName("Phiên RUNNING nhưng cascade không đặt bid nào → empty, không persist")
        void running_but_no_autobid_returns_empty() {
            // FakeAutoBidService mặc định không thêm bid (cascadeBehavior trả false)
            Optional<BidOutcome> result = bidService.triggerAutoBids(AUCTION_ID);

            assertTrue(result.isEmpty());
            assertEquals(1, autoBidService.resolveCount());   // cascade CÓ được gọi
            assertEquals(0, auction.getBidHistory().size());  // nhưng không đặt bid nào
            assertEquals(0, bidDao.savedCount());             // không persist thừa
        }

        @Test
        @DisplayName("Phiên RUNNING + cascade đặt 1 bid mở màn → trả outcome + persist + cập nhật state")
        void running_with_opening_bid_returns_outcome() {
            autoBidService.setCascadeBehavior(a -> {
                a.addBid(Bid.createNew(a.getId(), "auto-X", 5_100_000L, BidSource.AUTO));
                return true;
            });

            Optional<BidOutcome> result = bidService.triggerAutoBids(AUCTION_ID);

            assertTrue(result.isPresent());
            BidOutcome outcome = result.get();
            assertEquals(AUCTION_ID, outcome.auction().getId());
            assertEquals("auto-X", outcome.bid().getBidderId());
            assertEquals(5_100_000L, outcome.bid().getAmount());
            assertEquals(BidSource.AUTO, outcome.bid().getSource());
            assertEquals(0L, outcome.extendedSeconds());      // giá mở màn không anti-sniping

            assertEquals(5_100_000L, auction.getCurrentPrice());
            assertEquals("auto-X", auction.getHighestBidderId());
            assertEquals(1, bidDao.savedCount());
            assertEquals(0, bidDao.rollbackCount());
        }

        @Test
        @DisplayName("Cascade đặt nhiều bid → outcome.bid() là bid CUỐI, tất cả persist cùng transaction")
        void multiple_cascade_bids_outcome_is_last() {
            autoBidService.setCascadeBehavior(a -> {
                a.addBid(Bid.createNew(a.getId(), "auto-1", 5_100_000L, BidSource.AUTO));
                a.addBid(Bid.createNew(a.getId(), "auto-2", 5_300_000L, BidSource.AUTO));
                a.addBid(Bid.createNew(a.getId(), "auto-1", 5_500_000L, BidSource.AUTO));
                return true;
            });

            Optional<BidOutcome> result = bidService.triggerAutoBids(AUCTION_ID);

            assertTrue(result.isPresent());
            assertEquals(5_500_000L, result.get().bid().getAmount());   // bid cuối cùng
            assertEquals("auto-1", result.get().bid().getBidderId());
            assertEquals(3, bidDao.savedCount());
            assertEquals(0, bidDao.rollbackCount());
            assertEquals(5_500_000L, auction.getCurrentPrice());
        }

        @Test
        @DisplayName("Phiên FINISHED → empty, chặn TRƯỚC khi gọi cascade, không persist")
        void finished_auction_returns_empty_without_cascade() {
            auction.finish();
            auctionDao.save(auction);

            Optional<BidOutcome> result = bidService.triggerAutoBids(AUCTION_ID);

            assertTrue(result.isEmpty());
            assertEquals(0, autoBidService.resolveCount());   // không gọi cascade khi đã đóng
            assertEquals(0, bidDao.savedCount());
        }

        @Test
        @DisplayName("Phiên CANCELED → empty, không chạy cascade")
        void canceled_auction_returns_empty() {
            auction.cancel();
            auctionDao.save(auction);

            Optional<BidOutcome> result = bidService.triggerAutoBids(AUCTION_ID);

            assertTrue(result.isEmpty());
            assertEquals(0, autoBidService.resolveCount());
        }

        @Test
        @DisplayName("Gọi 2 lần liên tiếp: lần 2 không đặt thêm bid (lock nhả đúng sau lần 1)")
        void second_call_does_not_double_bid() {
            autoBidService.setCascadeBehavior(a -> {
                // chỉ đặt giá mở màn khi chưa có ai dẫn
                if (a.getBidHistory().isEmpty()) {
                    a.addBid(Bid.createNew(a.getId(), "auto-X", 5_100_000L, BidSource.AUTO));
                    return true;
                }
                return false;
            });

            Optional<BidOutcome> first = bidService.triggerAutoBids(AUCTION_ID);
            Optional<BidOutcome> second = bidService.triggerAutoBids(AUCTION_ID);

            assertTrue(first.isPresent());
            assertTrue(second.isEmpty());                     // lần 2 không đặt thêm
            assertEquals(1, auction.getBidHistory().size());
            assertEquals(2, autoBidService.resolveCount());   // cascade gọi đủ 2 lần
        }
    }

    @Nested
    @DisplayName("Concurrency — lock đảm bảo không lost update")
    class Concurrency {

        @Test
        @DisplayName("10 thread cùng bid → tổng bid history = số bid thành công, không lost update")
        void concurrent_bids_no_lost_update() throws Exception {
            int threadCount = 10;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger rejectedCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                userDao.save(new Bidder("b-" + i, "bidder" + i, "pwd", 100_000_000L));
            }

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                pool.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        long amount = 5_100_000L + (long) idx * 100_000L;
                        bidService.placeBid(AUCTION_ID, "b-" + idx, amount);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        rejectedCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
            pool.shutdown();

            assertEquals(threadCount, successCount.get() + rejectedCount.get());
            assertEquals(successCount.get(), auction.getBidHistory().size());

            assertEquals(
                    auction.getBidHistory().get(auction.getBidHistory().size() - 1).getAmount(),
                    auction.getCurrentPrice());

            assertEquals(successCount.get(), bidDao.savedCount());
            assertEquals(0, bidDao.rollbackCount());
        }

        @Test
        @DisplayName("Concurrency: thứ tự bid trong history luôn tăng dần (không đảo)")
        void concurrent_bids_history_monotonically_increasing() throws Exception {
            int threadCount = 8;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                userDao.save(new Bidder("c-" + i, "bidderc" + i, "pwd", 100_000_000L));
                final int idx = i;
                pool.submit(() -> {
                    try {
                        startLatch.await();
                        bidService.placeBid(AUCTION_ID, "c-" + idx,
                                5_100_000L + (long) idx * 200_000L);
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
            pool.shutdown();

            List<Bid> history = auction.getBidHistory();
            for (int i = 1; i < history.size(); i++) {
                assertTrue(
                        history.get(i).getAmount() > history.get(i - 1).getAmount(),
                        "Bid history phải tăng dần — không được đảo thứ tự");
            }
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
