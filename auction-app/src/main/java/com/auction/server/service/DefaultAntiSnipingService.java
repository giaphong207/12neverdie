package com.auction.server.service;

import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;

import java.time.Duration;
import java.time.LocalDateTime;

public class DefaultAntiSnipingService implements AntiSnipingService {

    private final Duration triggerWindow;       // vd: 60s
    private final Duration extensionDuration;   // vd: 60s

    public DefaultAntiSnipingService(Duration triggerWindow,
                                     Duration extensionDuration) {
        if (triggerWindow.isNegative() || triggerWindow.isZero()) {
            throw new IllegalArgumentException("triggerWindow phải dương");
        }
        if (extensionDuration.isNegative() || extensionDuration.isZero()) {
            throw new IllegalArgumentException("extensionDuration phải dương");
        }
        this.triggerWindow = triggerWindow;
        this.extensionDuration = extensionDuration;
    }

    @Override
    public boolean shouldExtend(Auction auction, LocalDateTime now) {
        if (auction.getStatus() != AuctionStatus.RUNNING) {
            return false;
        }
        Duration timeLeft = Duration.between(now, auction.getEndTime());
        // Đã quá hạn → không extend nữa (để scheduler/lazy sync finish nó)
        if (timeLeft.isNegative() || timeLeft.isZero()) {
            return false;
        }
        // Còn trong cửa sổ trigger → extend
        return timeLeft.compareTo(triggerWindow) <= 0;
    }

    @Override
    public long applyExtension(Auction auction) {
        long seconds = extensionDuration.toSeconds();
        auction.extendEndTime(seconds);   // ← dùng method có validate
        return seconds;
    }

    @Override
    public Duration getExtensionDuration() { return extensionDuration; }

    @Override
    public Duration getTriggerWindow()    { return triggerWindow; }
}