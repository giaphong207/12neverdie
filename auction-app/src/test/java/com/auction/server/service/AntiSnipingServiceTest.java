package com.auction.server.service;

import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AntiSnipingService - gia hạn 60s nếu bid trong phút cuối")
class AntiSnipingServiceTest {

    private DefaultAntiSnipingService service;

    @BeforeEach
    void setUp() {
        service = new DefaultAntiSnipingService();
    }

    @Test
    @DisplayName("Bid trong 60s cuối → end_time được gia hạn")
    void bid_in_last_60s_extends_endtime() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = now.plusSeconds(30);
        Auction auction = createAuction(now.minusMinutes(10), endTime);

        service.applyExtensionIfNeeded(auction, now);

        assertTrue(auction.getEndTime().isAfter(endTime),
                "End time phải được gia hạn khi bid trong phút cuối");
    }

    @Test
    @DisplayName("Bid trước 60s cuối → không gia hạn")
    void bid_before_last_60s_no_extension() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = now.plusMinutes(10);
        Auction auction = createAuction(now.minusMinutes(5), endTime);

        service.applyExtensionIfNeeded(auction, now);

        assertEquals(endTime, auction.getEndTime(),
                "End time không thay đổi vì còn nhiều thời gian");
    }

    @Test
    @DisplayName("Bid đúng giây 60 (biên) — không crash")
    void bid_at_exactly_60s_boundary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = now.plusSeconds(60);
        Auction auction = createAuction(now.minusMinutes(5), endTime);

        service.applyExtensionIfNeeded(auction, now);

        assertNotNull(auction.getEndTime(), "End time vẫn phải tồn tại");
    }

    private Auction createAuction(LocalDateTime start, LocalDateTime end) {
        return new Auction(
                UUID.randomUUID().toString(),
                "item-1",
                "seller-1",
                1000L, 100L,
                AuctionStatus.RUNNING, start, end);
    }
}