package com.auction.shared.networkMessage.request;

import com.auction.shared.model.ItemType;
import java.io.Serializable;

public class UpdateItemRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String itemId;
    private final String name;
    private final String description;
    private final long startPrice;
    private final ItemType type;
    private final String sellerId;

    public UpdateItemRequest(String itemId, String name, String description,
                             long startPrice, ItemType type, String sellerId) {
        this.itemId = itemId;
        this.name = name;
        this.description = description;
        this.startPrice = startPrice;
        this.type = type;
        this.sellerId = sellerId;
    }

    public String getItemId() { return itemId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public long getStartPrice() { return startPrice; }
    public ItemType getType() { return type; }
    public String getSellerId() { return sellerId; }
}