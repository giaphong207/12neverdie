package com.auction.server.service;

import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;
import com.auction.support.TestDataFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AntiSnipingService - gia hạn 60s nếu bid trong phút cuối")
class AntiSnipingServiceTest {

    private DefaultAntiSnipingService service;
    private static final Duration WINDOW = Duration.ofSeconds(60);
    private static final Duration EXTENSION = Duration.ofSeconds(60);

    @BeforeEach
    void setUp() {
        service = new DefaultAntiSnipingService(WINDOW, EXTENSION);
    }

    @Test
    @DisplayName("Constructor: triggerWindow âm/0 → IllegalArgumentException")
    void constructor_rejects_non_positive_trigger_window() {
        assertThrows(IllegalArgumentException.class,
                () -> new DefaultAntiSnipingService(Duration.ZERO, EXTENSION));
        assertThrows(IllegalArgumentException.class,
                () -> new DefaultAntiSnipingService(Duration.ofSeconds(-1), EXTENSION));
    }

    @Test
    @DisplayName("Constructor: extensionDuration âm/0 → IllegalArgumentException")
    void constructor_rejects_non_positive_extension() {
        assertThrows(IllegalArgumentException.class,
                () -> new DefaultAntiSnipingService(WINDOW, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new DefaultAntiSnipingService(WINDOW, Duration.ofSeconds(-1)));
    }

    @Test
    @DisplayName("Bid trong 20s cuối → shouldExtend = true")
    void should_extend_when_bid_in_last_seconds() {
        Auction auction = TestDataFactory.auctionAboutToEnd();
        LocalDateTime now = LocalDateTime.now();
        assertTrue(service.shouldExtend(auction, now));
    }

    @Test
    @DisplayName("Bid khi còn 300s → shouldExtend = false")
    void should_not_extend_when_plenty_of_time() {
        Auction auction = TestDataFactory.auctionWithPlentyOfTime();
        LocalDateTime now = LocalDateTime.now();
        assertFalse(service.shouldExtend(auction, now));
    }

    @Test
    @DisplayName("Boundary: bid đúng tại giây thứ 60 cuối → shouldExtend = true")
    void should_extend_exactly_at_window_boundary() {
        Auction auction = TestDataFactory.runningAuction(5_000_000L, 100_000L, 60);
        LocalDateTime now = LocalDateTime.now();
        assertTrue(service.shouldExtend(auction, now));
    }

    @Test
    @DisplayName("Boundary: bid khi còn 61s → shouldExtend = false")
    void should_not_extend_just_outside_window() {
        Auction auction = TestDataFactory.runningAuction(5_000_000L, 100_000L, 61);
        LocalDateTime now = LocalDateTime.now();
        assertFalse(service.shouldExtend(auction, now));
    }

    @Test
    @DisplayName("Auction đã hết hạn → shouldExtend = false")
    void should_not_extend_when_already_expired() {
        Auction auction = TestDataFactory.runningAuction(5_000_000L, 100_000L, 30);
        LocalDateTime futureNow = auction.getEndTime().plusSeconds(5);
        assertFalse(service.shouldExtend(auction, futureNow));
    }

    @Test
    @DisplayName("Auction không RUNNING (FINISHED) → shouldExtend = false")
    void should_not_extend_when_not_running() {
        Auction auction = TestDataFactory.finishedAuction();
        LocalDateTime now = LocalDateTime.now();
        assertFalse(service.shouldExtend(auction, now));
    }

    @Test
    @DisplayName("applyExtension trả về đúng số giây cấu hình")
    void apply_extension_returns_configured_seconds() {
        Auction auction = TestDataFactory.auctionAboutToEnd();
        long extended = service.applyExtension(auction);
        assertEquals(60L, extended);
    }

    @Test
    @DisplayName("applyExtension thực sự kéo dài endTime của auction")
    void apply_extension_extends_end_time() {
        Auction auction = TestDataFactory.auctionAboutToEnd();
        LocalDateTime before = auction.getEndTime();
        service.applyExtension(auction);
        Duration delta = Duration.between(before, auction.getEndTime());
        assertEquals(60L, delta.toSeconds());
    }

    @Test
    @DisplayName("applyExtension liên tiếp → tích lũy thêm thời gian")
    void apply_extension_accumulates() {
        Auction auction = TestDataFactory.auctionAboutToEnd();
        LocalDateTime before = auction.getEndTime();
        service.applyExtension(auction);
        service.applyExtension(auction);
        service.applyExtension(auction);
        Duration delta = Duration.between(before, auction.getEndTime());
        assertEquals(180L, delta.toSeconds());
    }

    @Test
    @DisplayName("applyExtension trên auction không RUNNING → IllegalStateException")
    void apply_extension_rejects_non_running() {
        Auction auction = TestDataFactory.finishedAuction();
        assertThrows(IllegalStateException.class,
                () -> service.applyExtension(auction));
    }

    @Test
    @DisplayName("Getters trả đúng cấu hình constructor")
    void getters_return_configured_values() {
        DefaultAntiSnipingService s = new DefaultAntiSnipingService(
                Duration.ofSeconds(30), Duration.ofSeconds(90));
        assertEquals(Duration.ofSeconds(30), s.getTriggerWindow());
        assertEquals(Duration.ofSeconds(90), s.getExtensionDuration());
    }

    @Test
    @DisplayName("Tích hợp: bid trong cửa sổ → trigger → apply → endTime tăng")
    void integration_trigger_then_apply() {
        Auction auction = TestDataFactory.auctionAboutToEnd();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endBefore = auction.getEndTime();

        assertTrue(service.shouldExtend(auction, now));
        long extendedSeconds = service.applyExtension(auction);

        assertEquals(60L, extendedSeconds);
        assertTrue(auction.getEndTime().isAfter(endBefore));
        assertEquals(AuctionStatus.RUNNING, auction.getStatus());
    }
}
