package com.auction.server.service;
import com.auction.shared.model.Item;
import java.util.List;


public interface ItemService {
    List<Item> getItemsBySeller(String sellerId);
    void addItem(Item item);
    void updateItem(Item item);
    void deleteItem(String itemId);
}
