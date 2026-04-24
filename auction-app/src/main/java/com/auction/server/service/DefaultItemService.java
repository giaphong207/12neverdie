package com.auction.server.service;

import com.auction.server.dao.ItemDao;
import com.auction.shared.model.Item;

import java.util.List;

public class DefaultItemService implements ItemService{
    private final ItemDao itemDao;
    public DefaultAuctionService(ItemDao itemDao){
        this.itemDao = itemDao;
    }
    @Override
    public List<Item> getItemsBySeller(String sellerId) {
        return itemDao.findBySellerId(sellerId);
    }
    @Override
    public void addItem(Item item) {
        if (itemDao.findById(item.getId()).isPresent()) {
            throw new IllegalArgumentException("Item đã tồn tại");
        }
        itemDao.save(item);
    }

    @Override
    public void updateItem(Item item) {
        if (itemDao.findById(item.getId()).isEmpty()) {
            throw new IllegalArgumentException("Item không tồn tại");
        }
        itemDao.save(item);
    }

    @Override
    public void deleteItem(String itemId){
        itemDao.deleteById(itemId);
    }
}
