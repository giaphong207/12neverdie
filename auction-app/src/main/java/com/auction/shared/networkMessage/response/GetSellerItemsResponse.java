package com.auction.shared.networkMessage.response;

import com.auction.shared.model.Item;
import java.io.Serializable;
import java.util.List;

public class GetSellerItemsResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String message;
    private final List<Item> items;

    public GetSellerItemsResponse(boolean success, String message, List<Item> items) {
        this.success = success;
        this.message = message;
        this.items = items;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public List<Item> getItems() { return items; }
}