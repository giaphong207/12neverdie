package com.auction.server.service;

import com.auction.shared.model.auction.Auction;
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
}
