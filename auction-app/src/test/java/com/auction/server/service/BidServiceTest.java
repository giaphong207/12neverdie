package com.auction.server.service;

import com.auction.server.dao.AuctionDao;
import com.auction.shared.exception.AuctionClosedException;
import com.auction.shared.exception.AuctionNotFoundException;
import com.auction.shared.exception.InvalidBidException;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BidServiceTest {

    private BidService bidService;
    private MockAuctionDao mockDao;

    @BeforeEach
    void setUp() {
        mockDao = new MockAuctionDao();
        // Cập nhật Tuần 3: Truyền thêm LifecycleService của TV4 vào để BidService hoạt động
        AuctionLifecycleService lifecycleService = new DefaultAuctionLifecycleService(mockDao);
        bidService = new DefaultBidService(mockDao, lifecycleService);
    }

    @Test
    void shouldPlaceBidSuccessfully() {
        LocalDateTime now = LocalDateTime.now();
        // Cập nhật: Thêm startTime và endTime vào Constructor
        Auction auction = new Auction("A1", "I1", "S1", 1000, 100, AuctionStatus.RUNNING, now.minusMinutes(10), now.plusMinutes(10));
        mockDao.save(auction);

        assertDoesNotThrow(() -> {
            bidService.placeBid("A1", "Bidder_1", 1200);
        });
    }

    @Test
    void shouldUpdateCurrentPriceAfterBid() {
        LocalDateTime now = LocalDateTime.now();
        Auction auction = new Auction("A2", "I1", "S1", 1000, 100, AuctionStatus.RUNNING, now.minusMinutes(10), now.plusMinutes(10));
        mockDao.save(auction);

        Auction result = bidService.placeBid("A2", "Bidder_1", 1500);

        assertEquals(1500, result.getCurrentPrice());
    }

    @Test
    void shouldUpdateHighestBidderAfterBid() {
        LocalDateTime now = LocalDateTime.now();
        Auction auction = new Auction("A3", "I1", "S1", 1000, 100, AuctionStatus.RUNNING, now.minusMinutes(10), now.plusMinutes(10));
        mockDao.save(auction);

        Auction result = bidService.placeBid("A3", "Bidder_2", 1200);

        assertEquals("Bidder_2", result.getHighestBidderId());
    }

    @Test
    void shouldThrowExceptionWhenBidTooLow() {
        LocalDateTime now = LocalDateTime.now();
        Auction auction = new Auction("A4", "I1", "S1", 1000, 100, AuctionStatus.RUNNING, now.minusMinutes(10), now.plusMinutes(10));
        mockDao.save(auction);

        // Giá hiện tại 1000, bước giá 100 -> Phải đặt từ 1100 trở lên. Đặt 1050 sẽ bị chặn.
        assertThrows(InvalidBidException.class, () -> {
            bidService.placeBid("A4", "Bidder_1", 1050);
        });
    }

    @Test
    void shouldThrowExceptionWhenAuctionClosed() {
        LocalDateTime now = LocalDateTime.now();
        // Phiên đấu giá đã kết thúc (FINISHED) và hết hạn thời gian
        Auction auction = new Auction("A5", "I1", "S1", 1000, 100, AuctionStatus.FINISHED, now.minusMinutes(20), now.minusMinutes(10));
        mockDao.save(auction);

        assertThrows(AuctionClosedException.class, () -> {
            bidService.placeBid("A5", "Bidder_1", 1500);
        });
    }

    @Test
    void shouldThrowExceptionWhenAuctionNotFound() {
        // Truyền một ID không hề tồn tại trong database
        assertThrows(AuctionNotFoundException.class, () -> {
            bidService.placeBid("ID_AO_DIEN", "Bidder_1", 1500);
        });
    }

    // ==========================================
    // LỚP MOCK DAO (Giả lập Database)
    // ==========================================
    static class MockAuctionDao implements AuctionDao {
        private final List<Auction> auctions = new ArrayList<>();

        @Override
        public List<Auction> findAll() {
            return auctions;
        }

        @Override
        public Optional<Auction> findById(String id) {
            return auctions.stream().filter(a -> a.getId().equals(id)).findFirst();
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
}