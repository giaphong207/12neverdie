package com.auction.server.realtime;

import com.auction.server.handler.ClientHandler;
import com.auction.shared.networkMessage.event.AuctionEvents.*;

public class EventBroadcaster {
    private final AuctionSubscriptionManager subscriptionManager;

    public EventBroadcaster(AuctionSubscriptionManager subscriptionManager) {
        this.subscriptionManager = subscriptionManager;
    }

    public void broadcast(AuctionEvent event) {

        // Gửi cho tất cả những người đang xem danh sách tổng
        for (ClientHandler handler : subscriptionManager.getListSubscribers()) {
            handler.send(event);
        }

        // Gửi cho tất cả những người đang ở trong phòng đấu giá cụ thể đó
        for (ClientHandler handler : subscriptionManager.getAuctionSubscribers(event.getAuction().getId())) {
            handler.send(event);
        }
    }
}