package com.auction.server.service;

import com.auction.server.concurrency.AuctionLockManager;
import com.auction.server.dao.AuctionDao;
import com.auction.shared.exception.AppException;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentBidServiceTest {

    private BidService bidService;
    private FakeAuctionDao auctionDao;
    private FakeAuctionLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        auctionDao = new FakeAuctionDao();
        lifecycleService = new FakeAuctionLifecycleService(auctionDao);
        bidService = new DefaultBidService(
                auctionDao,
                lifecycleService,
                new AuctionLockManager()
        );
    }

    @Test
    void shouldAcceptOnlyOneBidWhenTwoEqualBidsArriveAtSameTime() throws Exception {
        Auction auction = createRunningAuction("A001", 100, 10);
        auctionDao.save(auction);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> f1 = executor.submit(() -> tryBid("A001", "bidderA", 110, ready, start));
        Future<Boolean> f2 = executor.submit(() -> tryBid("A001", "bidderB", 110, ready, start));

        ready.await();
        start.countDown();

        boolean r1 = f1.get();
        boolean r2 = f2.get();

        Auction updated = auctionDao.findById("A001").orElseThrow();

        assertEquals(1, (r1 ? 1 : 0) + (r2 ? 1 : 0));
        assertEquals(110, updated.getCurrentPrice());
        assertEquals(1, updated.getBidHistory().size());

        executor.shutdown();
    }

    @Test
    void shouldSerializeConcurrentBidsCorrectly() throws Exception {
        Auction auction = createRunningAuction("A002", 100, 10);
        auctionDao.save(auction);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> f1 = executor.submit(() -> tryBid("A002", "bidderA", 120, ready, start));
        Future<Boolean> f2 = executor.submit(() -> tryBid("A002", "bidderB", 130, ready, start));

        ready.await();
        start.countDown();

        f1.get();
        f2.get();

        Auction updated = auctionDao.findById("A002").orElseThrow();

        assertEquals(130, updated.getCurrentPrice());
        assertEquals("bidderB", updated.getHighestBidderId());

        executor.shutdown();
    }

    @Test
    void shouldKeepHighestPriceConsistentAfterConcurrentBidding() throws Exception {
        Auction auction = createRunningAuction("A003", 100, 10);
        auctionDao.save(auction);

        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> f1 = executor.submit(() -> tryBid("A003", "bidderA", 120, ready, start));
        Future<Boolean> f2 = executor.submit(() -> tryBid("A003", "bidderB", 140, ready, start));
        Future<Boolean> f3 = executor.submit(() -> tryBid("A003", "bidderC", 130, ready, start));

        ready.await();
        start.countDown();

        f1.get();
        f2.get();
        f3.get();

        Auction updated = auctionDao.findById("A003").orElseThrow();

        assertEquals(140, updated.getCurrentPrice());
        assertEquals("bidderB", updated.getHighestBidderId());

        executor.shutdown();
    }

    private boolean tryBid(String auctionId,
                           String bidderId,
                           long amount,
                           CountDownLatch ready,
                           CountDownLatch start) {
        try {
            ready.countDown();
            start.await();
            bidService.placeBid(auctionId, bidderId, amount);
            return true;
        } catch (AppException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private Auction createRunningAuction(String id, long startPrice, long minIncrement) {
        LocalDateTime now = LocalDateTime.now();
        return new Auction(
                id,
                "item-" + id,
                "seller-" + id,
                startPrice,
                minIncrement,
                AuctionStatus.RUNNING,
                now.minusMinutes(5),
                now.plusMinutes(30)
        );
    }

    static class FakeAuctionDao implements AuctionDao {
        private final List<Auction> auctions = new ArrayList<>();

        @Override
        public List<Auction> findAll() {
            return auctions;
        }

        @Override
        public Optional<Auction> findById(String id) {
            return auctions.stream()
                    .filter(a -> a.getId().equals(id))
                    .findFirst();
        }

        @Override
        public void save(Auction auction) {
            auctions.removeIf(a -> a.getId().equals(auction.getId()));
            auctions.add(auction);
        }

        @Override
        public void deleteById(String id) {
            auctions.removeIf(a -> a.getId().equals(id));
        }

        @Override
        public List<Auction> findActiveAuctions() {
            return auctions.stream()
                    .filter(a -> a.getStatus() == AuctionStatus.OPEN || a.getStatus() == AuctionStatus.RUNNING)
                    .toList();
        }
    }

    static class FakeAuctionLifecycleService implements AuctionLifecycleService {
        private final AuctionDao auctionDao;

        FakeAuctionLifecycleService(AuctionDao auctionDao) {
            this.auctionDao = auctionDao;
        }

        @Override
        public Auction updateStatusByTime(String auctionId) {
            Auction auction = auctionDao.findById(auctionId).orElseThrow();
            auction.updateStatusByTime(LocalDateTime.now());
            auctionDao.save(auction);
            return auction;
        }

        @Override
        public void updateAllAuctionStatuses() {
            for (Auction auction : auctionDao.findAll()) {
                auction.updateStatusByTime(LocalDateTime.now());
                auctionDao.save(auction);
            }
        }

        @Override
        public void finishAuction(String auctionId) {
            Auction auction = auctionDao.findById(auctionId).orElseThrow();
            auction.finish();
            auctionDao.save(auction);
        }
    }
}