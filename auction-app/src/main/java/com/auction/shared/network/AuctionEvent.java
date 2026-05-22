package com.auction.shared.network;

import java.io.Serializable;

import com.auction.shared.model.Auction;

public abstract class AuctionEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Auction auction;

    public AuctionEvent(Auction auction) {
        this.auction = auction;
    }

    public Auction getAuction() {
        return auction;
    }
}