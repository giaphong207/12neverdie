package com.auction.server.realtime;

import com.auction.server.handler.ClientHandler;
import com.auction.shared.networkMessage.AuctionEvents.*;

public class EventBroadcaster {
    private final AuctionSubscriptionManager subscriptionManager;
    private final AuctionEnricher enricher;

    public EventBroadcaster(AuctionSubscriptionManager subscriptionManager,
                            AuctionEnricher enricher) {
        this.subscriptionManager = subscriptionManager;
        this.enricher = enricher;
    }

    public void broadcast(AuctionEvent event) {
        // Enrich auction với itemName, sellerName, highestBidderName trước khi phát
        enricher.enrich(event.getAuction());

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