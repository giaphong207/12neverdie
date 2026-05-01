package com.auction.server.service;

import com.auction.server.dao.AuctionDao;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;
import com.auction.shared.model.Bid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuctionLifecycleServiceTest {

    private AuctionLifecycleService lifecycleService;
    private FakeAuctionDao fakeAuctionDao;

    @BeforeEach
    void setUp() {
        fakeAuctionDao = new FakeAuctionDao();
        lifecycleService = new DefaultAuctionLifecycleService(fakeAuctionDao);
    }

    @Test
    void shouldKeepAuctionOpenBeforeStartTime() {
        LocalDateTime now = LocalDateTime.now();

        Auction auction = new Auction(
                "auction-open",
                "item-01",
                "seller-01",
                100_000L,
                10_000L,
                AuctionStatus.OPEN,
                now.plusMinutes(10),
                now.plusMinutes(20)
        );

        fakeAuctionDao.save(auction);

        Auction updated = lifecycleService.updateStatusByTime(auction.getId());

        assertEquals(AuctionStatus.OPEN, updated.getStatus());
    }

    @Test
    void shouldChangeAuctionToRunningWhenCurrentTimeIsBetweenStartAndEnd() {
        LocalDateTime now = LocalDateTime.now();

        Auction auction = new Auction(
                "auction-running",
                "item-02",
                "seller-01",
                100_000L,
                10_000L,
                AuctionStatus.OPEN,
                now.minusMinutes(5),
                now.plusMinutes(5)
        );

        fakeAuctionDao.save(auction);

        Auction updated = lifecycleService.updateStatusByTime(auction.getId());

        assertEquals(AuctionStatus.RUNNING, updated.getStatus());
    }

    @Test
    void shouldFinishAuctionWhenEndTimePassed() {
        LocalDateTime now = LocalDateTime.now();

        Auction auction = new Auction(
                "auction-finished",
                "item-03",
                "seller-01",
                100_000L,
                10_000L,
                AuctionStatus.RUNNING,
                now.minusMinutes(20),
                now.minusMinutes(5)
        );

        fakeAuctionDao.save(auction);

        Auction updated = lifecycleService.updateStatusByTime(auction.getId());

        assertEquals(AuctionStatus.FINISHED, updated.getStatus());
    }

    @Test
    void shouldSetWinnerWhenAuctionFinishedWithBid() {
        LocalDateTime now = LocalDateTime.now();

        Auction auction = new Auction(
                "auction-winner",
                "item-04",
                "seller-01",
                100_000L,
                10_000L,
                AuctionStatus.RUNNING,
                now.minusMinutes(5),
                now.plusMinutes(5)
        );

        Bid bid = new Bid(
                "bid-01",
                auction.getId(),
                "bidder-01",
                120_000L,
                now
        );

        auction.addBid(bid);
        fakeAuctionDao.save(auction);

        lifecycleService.finishAuction(auction.getId());

        Auction updated = fakeAuctionDao.findById(auction.getId()).orElseThrow();

        assertEquals(AuctionStatus.FINISHED, updated.getStatus());
        assertEquals("bidder-01", updated.getWinnerBidderId());
    }

    @Test
    void shouldHaveNoWinnerWhenAuctionFinishedWithoutBid() {
        LocalDateTime now = LocalDateTime.now();

        Auction auction = new Auction(
                "auction-no-winner",
                "item-05",
                "seller-01",
                100_000L,
                10_000L,
                AuctionStatus.RUNNING,
                now.minusMinutes(5),
                now.plusMinutes(5)
        );

        fakeAuctionDao.save(auction);

        lifecycleService.finishAuction(auction.getId());

        Auction updated = fakeAuctionDao.findById(auction.getId()).orElseThrow();

        assertEquals(AuctionStatus.FINISHED, updated.getStatus());
        assertNull(updated.getWinnerBidderId());
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
                    .filter(a -> a.getStatus() == AuctionStatus.OPEN
                            || a.getStatus() == AuctionStatus.RUNNING)
                    .toList();
        }
    }
}