package com.auction.shared.networkMessage.event;

import com.auction.shared.model.Auction;

public class AuctionPaidEvent extends AuctionEvent {
    public AuctionPaidEvent(Auction auction) {
        super(auction);
    }
}
