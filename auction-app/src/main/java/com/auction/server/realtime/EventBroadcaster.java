package com.auction.server.realtime;

import com.auction.server.handler.ClientHandler;
import com.auction.shared.model.Auction;
import com.auction.shared.network.AuctionUpdateEvent;

public class EventBroadcaster {
    private final AuctionSubscriptionManager subscriptionManager;

    public EventBroadcaster(AuctionSubscriptionManager subscriptionManager) {
        this.subscriptionManager = subscriptionManager;
    }

    public void broadcastAuctionUpdate(Auction auction) {
        AuctionUpdateEvent event = new AuctionUpdateEvent(auction);

        // Gửi cho tất cả những người đang xem danh sách tổng
        for (ClientHandler handler : subscriptionManager.getListSubscribers()) {
            handler.send(event);
        }

        // Gửi cho tất cả những người đang ở trong phòng đấu giá cụ thể đó
        for (ClientHandler handler : subscriptionManager.getAuctionSubscribers(auction.getId())) {
            handler.send(event);
        }
    }
}