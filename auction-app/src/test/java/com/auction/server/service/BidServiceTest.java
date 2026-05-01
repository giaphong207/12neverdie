package com.auction.server.service;

import com.auction.server.dao.AuctionDao;
import com.auction.server.service.BidService;
import com.auction.server.service.DefaultBidService;
import com.auction.shared.exception.AuctionClosedException;
import com.auction.shared.exception.AuctionNotFoundException;
import com.auction.shared.exception.InvalidBidException;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BidServiceTest {

    private BidService bidService;
    private MockAuctionDao mockDao;

    @BeforeEach
    void setUp() {
        mockDao = new MockAuctionDao();
        bidService = new DefaultBidService(mockDao);
    }

    @Test
    void shouldPlaceBidSuccessfully() {
        Auction auction = new Auction("A1", "I1", "S1", 1000, 100, AuctionStatus.RUNNING, LocalDateTime.now().plusDays(1));
        mockDao.save(auction);

        assertDoesNotThrow(() -> {
            bidService.placeBid("A1", "Bidder_1", 1200);
        });
    }

    @Test
    void shouldUpdateCurrentPriceAfterBid() {
        Auction auction = new Auction("A2", "I1", "S1", 1000, 100, AuctionStatus.RUNNING, LocalDateTime.now().plusDays(1));
        mockDao.save(auction);

        Auction result = bidService.placeBid("A2", "Bidder_1", 1500);
        assertEquals(1500, result.getCurrentPrice());
    }

    @Test
    void shouldUpdateHighestBidderAfterBid() {
        Auction auction = new Auction("A3", "I1", "S1", 1000, 100, AuctionStatus.RUNNING, LocalDateTime.now().plusDays(1));
        mockDao.save(auction);

        Auction result = bidService.placeBid("A3", "Vip_Bidder", 2000);
        assertEquals("Vip_Bidder", result.getHighestBidderId());
    }

    @Test
    void shouldAddBidToHistory() {
        Auction auction = new Auction("A4", "I1", "S1", 1000, 100, AuctionStatus.RUNNING, LocalDateTime.now().plusDays(1));
        mockDao.save(auction);

        bidService.placeBid("A4", "Bidder_1", 1200);
        bidService.placeBid("A4", "Bidder_2", 1500);

        Auction result = mockDao.findById("A4").get();
        assertEquals(2, result.getBids().size());
        assertEquals(1500, result.getBids().get(1).getAmount());
    }

    @Test
    void shouldRejectBidLowerThanRequiredPrice() {
        Auction auction = new Auction("A5", "I1", "S1", 1000, 100, AuctionStatus.RUNNING, LocalDateTime.now().plusDays(1));
        mockDao.save(auction);

        assertThrows(InvalidBidException.class, () -> {
            bidService.placeBid("A5", "Bidder_1", 1050); // Min = 1000 + 100 = 1100
        });
    }

    @Test
    void shouldRejectBidWhenAuctionFinished() {
        Auction auction = new Auction("A6", "I1", "S1", 1000, 100, AuctionStatus.FINISHED, LocalDateTime.now().minusDays(1));
        mockDao.save(auction);

        assertThrows(AuctionClosedException.class, () -> {
            bidService.placeBid("A6", "Bidder_1", 1500);
        });
    }

    @Test
    void shouldThrowExceptionWhenAuctionNotFound() {
        assertThrows(AuctionNotFoundException.class, () -> {
            bidService.placeBid("WRONG_ID", "Bidder_1", 5000);
        });
    }

    // --- MOCK DAO ---
    private static class MockAuctionDao implements AuctionDao {
        private java.util.Map<String, Auction> database = new java.util.HashMap<>();
        @Override public void save(Auction a) { database.put(a.getId(), a); }
        @Override public void deleteById(String id) { database.remove(id);}
        @Override public Optional<Auction> findById(String id) { return Optional.ofNullable(database.get(id)); }
        @Override public java.util.List<Auction> findAll() { return new java.util.ArrayList<>(database.values()); }
        @Override public java.util.List<Auction> findActiveAuctions() { return null; }
    }
}
