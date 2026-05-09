package com.auction.shared.network;

import com.auction.shared.model.Auction;
import java.io.Serializable;

public class BidResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String message;
    private final Auction updatedAuction;

    public BidResponse(boolean success, String message, Auction updatedAuction) {
        this.success = success; //trả về true/fasle - thành công hay khôg
        this.message = message; // thông báo
        this.updatedAuction = updatedAuction; //obj Auction đã có giá mới
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Auction getUpdatedAuction() { return updatedAuction; }
}