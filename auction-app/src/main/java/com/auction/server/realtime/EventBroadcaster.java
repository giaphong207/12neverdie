package com.auction.server.realtime;

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