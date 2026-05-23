package com.auction.shared.network;

import com.auction.shared.model.Auction;

public class AuctionUpdatedEvent extends AuctionEvent{
    public AuctionUpdatedEvent(Auction auction) {
        super(auction);
    }
}
