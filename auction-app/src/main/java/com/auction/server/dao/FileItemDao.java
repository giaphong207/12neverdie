package com.auction.server.dao;

import com.auction.shared.model.Item;
import java.util.List;
import java.util.Optional;

public class FileItemDao implements ItemDao {
    @Override
    public List<Item> findAll() {
        return DataManager.getInstance().getStore().getItems();
    }

    @Override
    public Optional<Item> findById(String id) {
        // Tạm thời return empty nếu Item chưa có hàm getId()
        return findAll().stream().filter(i -> id.equals(i.getId())).findFirst();
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