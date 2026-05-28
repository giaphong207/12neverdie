package com.auction.server.realtime;

import com.auction.shared.networkMessage.AuctionEvents.*;

public class EventBroadcaster {
    private final AuctionSubscriptionManager subscriptionManager;

    public EventBroadcaster(AuctionSubscriptionManager subscriptionManager) {
        this.subscriptionManager = subscriptionManager;
    }

    public void broadcast(AuctionEvent event) {
        // Người đang xem danh sách tổng
        for (EventReceiver sink : subscriptionManager.getListSubscribers()) {
            sink.send(event);
        }
        // Người đang xem chi tiết đúng phiên đó
        for (EventReceiver sink : subscriptionManager.getAuctionSubscribers(event.getAuction().getId())) {
            sink.send(event);
        }
    }
}