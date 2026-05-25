package com.auction.shared.networkMessage.request;

import java.io.Serializable;

public class DeleteItemRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String itemId;

    public DeleteItemRequest(String itemId) {
        this.itemId = itemId;
    }

    public String getItemId() { return itemId; }
}