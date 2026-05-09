package com.auction.shared.network;

import java.io.Serializable;

public class BidRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String auctionId;
    private final String bidderId;
    private final long amount;

    public BidRequest(String auctionId, String bidderId, long amount) {
        this.auctionId = auctionId; //Đặt phiên nào
        this.bidderId = bidderId; //Ai đặt
        this.amount = amount;  //Đặt bao nhiêu
    }

    public String getAuctionId() { return auctionId; }
    public String getBidderId() { return bidderId; }
    public long getAmount() { return amount; }
}