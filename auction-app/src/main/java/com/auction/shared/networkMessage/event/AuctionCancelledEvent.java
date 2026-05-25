package com.auction.shared.networkMessage.event;

import com.auction.shared.model.Auction;

public class AuctionCancelledEvent extends AuctionEvent {
    public AuctionCancelledEvent(Auction auction) {
        super(auction);
    }
}
