package com.auction.server.service;

import com.auction.shared.config.AppConfig;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;
import com.auction.server.service.AntiSnipingService;
import com.auction.server.service.DefaultAntiSnipingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AntiSnipingServiceTest {

    private AntiSnipingService antiSnipingService;

    // Helper tạo auction để dùng trong test — không phải mock, là object thật
    private Auction makeRunningAuction(long secondsUntilEnd) {
        LocalDateTime now   = LocalDateTime.now();
        LocalDateTime start = now.minusMinutes(10);           // đã bắt đầu 10 phút trước
        LocalDateTime end   = now.plusSeconds(secondsUntilEnd);

        Auction a = new Auction(
                "AUC-TEST", "item-1", "seller-1",
                1_000_000L,   // startPrice
                100_000L,     // minIncrement
                AuctionStatus.RUNNING,
                start, end
        );
        return a;
    }

    @BeforeEach
    void setUp() {
        antiSnipingService = new DefaultAntiSnipingService();
    }

    // ── Test 1: bid ở 20 giây cuối → phải gia hạn ──────────────────────────
    @Test
    void shouldExtendAuctionWhenBidArrivesInLast30Seconds() {
        // Setup: phiên còn 20 giây (< 30 giây trigger)
        Auction auction = makeRunningAuction(20);
        LocalDateTime endTimeBefore = auction.getEndTime();

        boolean result = antiSnipingService.applyExtensionIfNeeded(auction, LocalDateTime.now());

        assertTrue(result, "Phải trả về true khi gia hạn");

        // endTime phải tăng thêm đúng 60 giây
        LocalDateTime expected = endTimeBefore.plusSeconds(AppConfig.ANTI_SNIPING_EXTENSION_SECONDS);
        assertEquals(expected, auction.getEndTime(),
                "endTime phải tăng thêm " + AppConfig.ANTI_SNIPING_EXTENSION_SECONDS + "s");
    }

    // ── Test 2: bid khi còn 5 giây → gia hạn ──────────────────────────────
    @Test
    void shouldExtendAuctionWhenBidArrivesInLast5Seconds() {
        Auction auction = makeRunningAuction(5);
        LocalDateTime endTimeBefore = auction.getEndTime();

        boolean result = antiSnipingService.applyExtensionIfNeeded(auction, LocalDateTime.now());

        assertTrue(result);
        assertEquals(endTimeBefore.plusSeconds(60), auction.getEndTime());
    }

    // ── Test 3: bid khi còn đúng 30 giây → đúng ngưỡng, phải gia hạn ──────
    @Test
    void shouldExtendWhenExactlyAtTriggerBoundary() {
        // Dùng triggerTime cách endTime đúng 30 giây
        LocalDateTime now   = LocalDateTime.now();
        LocalDateTime end   = now.plusSeconds(AppConfig.ANTI_SNIPING_TRIGGER_SECONDS);
        Auction auction = new Auction(
                "AUC-TEST2", "item-1", "seller-1",
                1_000_000L, 100_000L,
                AuctionStatus.RUNNING,
                now.minusMinutes(5), end
        );

        boolean result = antiSnipingService.applyExtensionIfNeeded(auction, now);

        assertTrue(result, "Đúng ngưỡng 30 giây phải gia hạn");
    }

    // ── Test 4: bid khi còn nhiều thời gian → không gia hạn ────────────────
    @Test
    void shouldNotExtendAuctionWhenMoreThan30SecondsRemain() {
        // Setup: phiên còn 120 giây (> 30 giây trigger)
        Auction auction = makeRunningAuction(120);
        LocalDateTime endTimeBefore = auction.getEndTime();

        boolean result = antiSnipingService.applyExtensionIfNeeded(auction, LocalDateTime.now());

        assertFalse(result, "Phải trả về false khi còn nhiều thời gian");
        assertEquals(endTimeBefore, auction.getEndTime(), "endTime không được thay đổi");
    }

    // ── Test 5: phiên không RUNNING → không gia hạn ─────────────────────────
    @Test
    void shouldNotExtendAuctionIfAuctionIsNotRunning() {
        LocalDateTime now = LocalDateTime.now();
        // Tạo auction đã FINISHED
        Auction auction = new Auction(
                "AUC-DONE", "item-1", "seller-1",
                1_000_000L, 100_000L,
                AuctionStatus.FINISHED,
                now.minusHours(2), now.minusHours(1)
        );

        boolean result = antiSnipingService.applyExtensionIfNeeded(auction, now);

        assertFalse(result, "Phiên FINISHED không được gia hạn");
    }

    // ── Test 6: endTime mới phải lớn hơn endTime cũ ─────────────────────────
    @Test
    void shouldPersistNewEndTimeAfterExtension() {
        Auction auction = makeRunningAuction(10);
        LocalDateTime endTimeBefore = auction.getEndTime();

        antiSnipingService.applyExtensionIfNeeded(auction, LocalDateTime.now());

        assertTrue(auction.getEndTime().isAfter(endTimeBefore),
                "endTime mới phải sau endTime cũ");
    }

    // ── Test 7: gọi 2 lần (simulate auto-bid cascade) → chỉ extend 1 lần ───
    // Lưu ý: rule này enforce ở DefaultBidService (chỉ gọi 1 lần cho manual bid)
    // Test này verify rằng extend 2 lần sẽ cộng 120s → không phải 60s
    // để nhóm biết phải chặn ở tầng service, không phải ở đây
    @Test
    void shouldExtendTwiceIfCalledTwice_ThusCallerMustCallOnce() {
        Auction auction = makeRunningAuction(10);
        LocalDateTime endTimeBefore = auction.getEndTime();

        antiSnipingService.applyExtensionIfNeeded(auction, LocalDateTime.now());
        // Lần 2: sau lần 1, còn 70 giây → không trigger nữa
        boolean secondCall = antiSnipingService.applyExtensionIfNeeded(auction, LocalDateTime.now());

        assertFalse(secondCall, "Lần 2 không trigger vì đã gia hạn, còn 70 giây");
        // Tổng endTime chỉ tăng 60s, không phải 120s
        assertEquals(endTimeBefore.plusSeconds(60), auction.getEndTime(),
                "Chỉ gia hạn đúng 1 lần = 60 giây");
    }
}