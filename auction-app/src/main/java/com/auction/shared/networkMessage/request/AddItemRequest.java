package com.auction.shared.networkMessage.request;

import com.auction.shared.model.ItemType;
import java.io.Serializable;

public class AddItemRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final String description;
    private final long startPrice;
    private final ItemType type;
    private final String sellerId;

    public AddItemRequest(String name, String description, long startPrice,
                          ItemType type, String sellerId) {
        this.name = name;
        this.description = description;
        this.startPrice = startPrice;
        this.type = type;
        this.sellerId = sellerId;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public long getStartPrice() { return startPrice; }
    public ItemType getType() { return type; }
    public String getSellerId() { return sellerId; }
}