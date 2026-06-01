package com.auction.server.service;

import java.util.List; 
import java.util.UUID;

import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.ItemDao;
import com.auction.shared.exception.AppExceptions.*;
import com.auction.shared.exception.AppExceptions.InvalidItemException;
import com.auction.shared.exception.AppExceptions.ItemNotFoundException;
import com.auction.shared.factory.ItemFactory;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.item.ItemType;

public class DefaultItemService implements ItemService {
    private final ItemDao itemDao;
    private final AuctionDao auctionDao;

    public DefaultItemService(ItemDao itemDao, AuctionDao auctionDao) {
        if (itemDao == null) {
            throw new InvalidItemException("ItemDao không được null");
        }
        if (auctionDao == null) {
            throw new InvalidItemException("AuctionDao không được null");
        }
        this.itemDao = itemDao;
        this.auctionDao = auctionDao;
    }

    @Override
    public List<Item> getItemsBySeller(String sellerId) {
        requireNonBlank(sellerId, "sellerId");
        return itemDao.findBySellerId(sellerId);
    }

    @Override
    public Item addItem(String sellerId, String name, String description,
                        long startPrice, ItemType type) {
        validateFields(sellerId, name, description, startPrice, type);

        String newId = UUID.randomUUID().toString();
        Item item = ItemFactory.createItem(type, newId, sellerId, name, description, startPrice);
        itemDao.save(item);
        return item;
    }

    @Override
    public Item updateItem(String itemId, String sellerId, String name,
                           String description, long startPrice, ItemType type) {
        requireNonBlank(itemId, "itemId");
        validateFields(sellerId, name, description, startPrice, type);

        Item existing = itemDao.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(itemId));

        // Authorization: chỉ chủ sở hữu mới được sửa
        if (!existing.getSellerId().equals(sellerId)) {
            throw new InvalidItemException("Bạn không có quyền sửa sản phẩm này");
        }

        // Item immutable → re-create với cùng ID (UPSERT)
        Item updated = ItemFactory.createItem(type, itemId, sellerId, name, description, startPrice);
        itemDao.save(updated);
        return updated;
    }

    @Override
    public void deleteItem(String itemId, String sellerId) {
        requireNonBlank(itemId, "itemId");
        requireNonBlank(sellerId, "sellerId");

        Item existing = itemDao.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(itemId));

        if (!existing.getSellerId().equals(sellerId)) {
            throw new InvalidItemException("Bạn không có quyền xóa sản phẩm này");
        }

        itemDao.deleteById(itemId);
    }

    @Override
    public void deleteItemAsAdmin(String itemId) {
        requireNonBlank(itemId, "itemId");

        itemDao.findById(itemId) //item phải tổn tại (ko ktra chủ sở hữu)
                .orElseThrow(() -> new ItemNotFoundException(itemId));

        for (Auction a : auctionDao.findAll()) {
            if (itemId.equals(a.getItemId())) {
                auctionDao.deleteById(a.getId());
            }
        }

        itemDao.deleteById(itemId);
    }

    // ───── helpers ─────

    private void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidItemException(fieldName + " không được để trống");
        }
    }

    private void validateFields(String sellerId, String name, String description,
                                long startPrice, ItemType type) {
        requireNonBlank(sellerId, "sellerId");
        requireNonBlank(name, "Tên sản phẩm");
        requireNonBlank(description, "Mô tả");
        if (startPrice <= 0) {
            throw new InvalidItemException("Giá khởi điểm phải lớn hơn 0");
        }
        if (type == null) {
            throw new InvalidItemException("Loại sản phẩm không được để trống");
        }
    }
}
