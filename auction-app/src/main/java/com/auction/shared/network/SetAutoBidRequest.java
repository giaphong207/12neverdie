package com.auction.shared.network;

import java.io.Serializable;

/**
 * Message từ client gửi lên server để tạo / cập nhật AutoBidConfig.
 */
public class SetAutoBidRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String auctionId;
    private final String bidderId;
    private final long maxAmount;
    private final long increment;

    public SetAutoBidRequest(String auctionId, String bidderId,
                             long maxAmount, long increment) {
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.maxAmount = maxAmount;
        this.increment = increment;
    }

    public String getAuctionId() { return auctionId; }
    public String getBidderId()  { return bidderId; }
    public long getMaxAmount()   { return maxAmount; }
    public long getIncrement()   { return increment; }
}