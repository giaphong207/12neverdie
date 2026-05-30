package com.auction.server.service;

import com.auction.shared.model.item.Item;
import com.auction.shared.model.item.ItemType;
import java.util.List;

public interface ItemService {
    List<Item> getItemsBySeller(String sellerId);

    Item addItem(String sellerId, String name, String description,
                 long startPrice, ItemType type);

    Item updateItem(String itemId, String sellerId, String name,
                    String description, long startPrice, ItemType type);

    void deleteItem(String itemId, String sellerId);
}
