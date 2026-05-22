package com.auction.shared.network;

import com.auction.shared.model.Auction;

public class AuctionExtendedEvent extends AuctionEvent{
    public AuctionExtendedEvent(Auction auction) {
        super(auction);
    }
}
