package com.auction.shared.networkMessage.request;

import java.io.Serializable;

public class SubscribeAuctionRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String auctionId; //chứa id để biết đang ngồi trong phòng nào (khi bấm vào xem phòng)

    public SubscribeAuctionRequest(String auctionId) {
        this.auctionId = auctionId;
    }

    public String getAuctionId() { return auctionId; }
}