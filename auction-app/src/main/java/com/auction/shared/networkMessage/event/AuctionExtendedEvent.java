package com.auction.shared.networkMessage.event;

import com.auction.shared.model.Auction;

public class AuctionExtendedEvent extends AuctionEvent {
    public AuctionExtendedEvent(Auction auction) {
        super(auction);
    }
}
