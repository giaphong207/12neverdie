package com.auction.shared.networkMessage.request;

import java.io.Serializable;

public class GetSellerItemsRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String sellerId;

    public GetSellerItemsRequest(String sellerId) {
        this.sellerId = sellerId;
    }

    public String getSellerId() { return sellerId; }
}