package com.auction.shared.networkMessage.event;

import com.auction.shared.model.Auction;

public class AuctionStartedEvent extends AuctionEvent {
    public AuctionStartedEvent(Auction auction) {
        super(auction);
    }
}
