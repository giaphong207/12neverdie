package com.auction.shared.network;

import com.auction.shared.model.Auction;

public class AuctionEndedEvent extends AuctionEvent{
    public AuctionEndedEvent(Auction auction) {
        super(auction);
    }
}
