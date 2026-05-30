package com.auction.client.realtime;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auction.shared.networkMessage.AuctionEvents.*;

import javafx.application.Platform;

/**
 * EventBus quản lý việc phát sự kiện đấu giá theo Observer Pattern.
 * Sử dụng Singleton pattern và thread-safe.
 *
 * Luồng hoạt động:
 * 1. RealtimeListener nhận AuctionUpdateEvent từ server
 * 2. Gọi publish(event)
 * 3. AuctionEventBus thông báo cho tất cả observer
 * 4. Controller cập nhật UI trên JavaFX Application Thread
 */
public final class AuctionEventBus {

    private static final Logger log = LoggerFactory.getLogger(AuctionEventBus.class);

    private static volatile AuctionEventBus instance;

    private final List<AuctionEventObserver> observers = new CopyOnWriteArrayList<>();

    private AuctionEventBus() {}

    public static AuctionEventBus getInstance() {
        if (instance == null) {
            synchronized (AuctionEventBus.class) {
                if (instance == null) {
                    instance = new AuctionEventBus();
                }
            }
        }
        return instance;
    }

    public void addObserver(AuctionEventObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
            log.info("+1 {} | total = {}", observer.getClass().getSimpleName(), observers.size());
        }
    }

    public void removeObserver(AuctionEventObserver observer) {
        if (observer != null && observers.remove(observer)) {
            log.info("-1 {} | total = {}", observer.getClass().getSimpleName(), observers.size());
        }
    }

    public void publish(AuctionEvent event) {
        if (event == null) {
            return;
        }

        log.debug("publish to {} observers", observers.size());

        Platform.runLater(() -> {
            for (AuctionEventObserver observer : observers) {
                try {
                    observer.onAuctionEvent(event);
                } catch (Exception e) {
                    log.error("Lỗi cập nhật observer {}",
                            observer.getClass().getSimpleName(), e);
                }
            }
        });
    }

    public int getObserverCount() {
        return observers.size();
    }
}