package com.auction.server.DAO;

import com.auction.shared.model.Item;
import java.util.List;
import java.util.Optional;

public interface ItemDao {
    List<Item> findAll();
    Optional<Item> findById(String id);
    List<Item> findBySellerId(String sellerId);
    void save(Item item);
    void deleteById(String id);
}