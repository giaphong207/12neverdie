package com.auction.shared.network;

import com.auction.shared.model.Auction;
import com.auction.shared.model.Bid;

public class BidPlacedEvent extends AuctionEvent{
    private Bid bid;
    public BidPlacedEvent(Auction auction, Bid bid) {
        super(auction);
        this.bid = bid;
    }

    public Bid getBid() {
        return bid;
    }
}
