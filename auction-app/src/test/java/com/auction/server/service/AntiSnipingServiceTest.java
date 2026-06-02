package com.auction.server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

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
}
