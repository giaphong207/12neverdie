package com.auction.shared.network;

import com.auction.shared.model.Item;
import java.io.Serializable;

public class UpdateItemResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String message;
    private final Item item;

    public UpdateItemResponse(boolean success, String message, Item item) {
        this.success = success;
        this.message = message;
        this.item = item;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Item getItem() { return item; }
}