package com.auction.shared.network;

import java.io.Serializable;

public class SubscribeAuctionRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String auctionId;

    public SubscribeAuctionRequest(String auctionId) {
        this.auctionId = auctionId;
    }

    public String getAuctionId() { return auctionId; }
}