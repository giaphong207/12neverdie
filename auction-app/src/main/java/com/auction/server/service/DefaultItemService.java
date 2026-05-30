package com.auction.server.service;

import com.auction.server.dao.ItemDao;
import com.auction.shared.exception.AppExceptions.*;
import com.auction.shared.factory.ItemFactory;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.item.ItemType;

import java.util.List;
import java.util.UUID;

public class DefaultItemService implements ItemService {
    private final ItemDao itemDao;

    public DefaultItemService(ItemDao itemDao) {
        if (itemDao == null) {
            throw new InvalidItemException("ItemDao không được null");
        }
        this.itemDao = itemDao;
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
