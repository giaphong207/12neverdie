package com.auction.server.DAO;

import com.auction.shared.model.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FileItemDao implements ItemDao {
    private final List<Item> items = new ArrayList<>();

    @Override
    public List<Item> findAll() {
        return DataManager.getInstance().getStore().getItems();
    }

    @Override
    public Optional<Item> findById(String id) {
        return findAll().stream().filter(i -> id.equals(i.getId())).findFirst();
    }
    @Override
    public List<Item> findBySellerId(String sellerId) {
        return items.stream()
                .filter(item -> item.getSellerId().equals(sellerId))
                .toList();
    }
    @Override
    public void save(Item item) {
        AppDataStore store = DataManager.getInstance().getStore();
        store.getItems().removeIf(i -> i.getId() != null && i.getId().equals(item.getId()));
        store.getItems().add(item);
        DataManager.getInstance().save(store);
    }

    @Override
    public void deleteById(String id) {
        AppDataStore store = DataManager.getInstance().getStore();
        store.getItems().removeIf(i -> i.getId() != null && i.getId().equals(id));
        DataManager.getInstance().save(store);
    }
}