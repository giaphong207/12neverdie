package com.auction.server.service;

import com.auction.server.dao.ItemDao;
import com.auction.shared.exception.AppExceptions.*;
import com.auction.shared.factory.ItemFactory;
import com.auction.shared.model.item.Item;

import java.util.List;

public class DefaultItemService implements ItemService{
    private final ItemDao itemDao;
    public DefaultItemService(ItemDao itemDao){
        if (itemDao == null) {
            throw new InvalidItemException("ItemDao không được trống");
        }
        this.itemDao = itemDao;
    }
    @Override
    public List<Item> getItemsBySeller(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new InvalidItemException("SellerId không được để trống");
        }

        return itemDao.findBySellerId(sellerId);
    }

    @Override
    public void addItem(Item item) {
        validateItem(item);

        if (itemDao.findById(item.getId()).isPresent()) {
            throw new InvalidItemException("Item đã tồn tại: " + item.getId());
        }

        itemDao.save(item);
    }

    @Override
    public void updateItem(Item item) {
        validateItem(item);

        if (itemDao.findById(item.getId()).isEmpty()) {
            throw new ItemNotFoundException(item.getId());
        }

        itemDao.save(item);
    }

    @Override
    public void deleteItem(String itemId){
        if (itemId == null || itemId.isBlank()) {
            throw new InvalidItemException("ItemId không được để trống");
        }

        if (itemDao.findById(itemId).isEmpty()) {
            throw new ItemNotFoundException(itemId);
        }

        itemDao.deleteById(itemId);
    }
    private void validateItem(Item item) {
        if (item == null) {
            throw new InvalidItemException("Sản phẩm không được trống");
        }

        if (item.getId() == null || item.getId().isBlank()) {
            throw new InvalidItemException("Id sản phẩm không được để trống");
        }

        if (item.getSellerId() == null || item.getSellerId().isBlank()) {
            throw new InvalidItemException("SellerId không được để trống");
        }

        if (item.getName() == null || item.getName().isBlank()) {
            throw new InvalidItemException("Tên sản phẩm không được để trống");
        }
        if (item.getDescription() == null || item.getDescription().isBlank()) {
            throw new InvalidItemException("Mô tả không được để trống");
        }

        if (item.getStartPrice() <= 0) {
            throw new InvalidItemException("Giá khởi điểm phải lớn hơn 0");
        }

        if (ItemFactory.toItemType(item) == null) {
            throw new InvalidItemException("Loại sản phẩm không được để trống");
        }
    }
}
