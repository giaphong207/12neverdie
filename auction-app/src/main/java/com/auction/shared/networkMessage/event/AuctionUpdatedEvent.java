package com.auction.shared.networkMessage.event;

import com.auction.shared.model.Auction;

public class AuctionUpdatedEvent extends AuctionEvent {
    public AuctionUpdatedEvent(Auction auction) {
        super(auction);
    }
}
