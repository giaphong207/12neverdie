package com.auction.client.realtime;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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

    private static volatile AuctionEventBus instance;
    
    private final List<AuctionEventObserver> observers = new CopyOnWriteArrayList<>();

    private AuctionEventBus() {}

    public static  AuctionEventBus getInstance() {
        if (instance == null) {
            synchronized (AuctionEventBus.class){
                if (instance == null){
                    instance = new AuctionEventBus();
                }
            }
        }
        return instance;
    }

    public void addObserver(AuctionEventObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
            System.out.println("Đăng ký observer: " + observer.getClass().getSimpleName());
        }
    }

    public void removeObserver(AuctionEventObserver observer) {
        if (observer != null) {
            observers.remove(observer);
            System.out.println("Hủy đăng ký observer: " + observer.getClass().getSimpleName());
        }
    }

    public void publish(AuctionEvent event) {
        if (event == null) {
            return;
        }

        System.out.println("Phát AuctionUpdateEvent cho " + observers.size() + " observer");

        Platform.runLater(() -> {
            for (AuctionEventObserver observer : observers) {
                try {
                    observer.onAuctionEvent(event);
                } catch (Exception e) {
                    System.err.println("Lỗi cập nhật observer " 
                            + observer.getClass().getSimpleName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    public int getObserverCount() {
        return observers.size();
    }
}