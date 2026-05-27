package com.auction.server.service;

import com.auction.server.dao.ItemDao;
import com.auction.shared.model.item.Item;
import com.auction.shared.factory.ItemFactory;

import com.auction.shared.model.item.ItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ItemService - CRUD sản phẩm")
class ItemServiceTest {

    private FakeItemDao itemDao;

    @BeforeEach
    void setUp() {
        itemDao = new FakeItemDao();
    }

    @Test
    @DisplayName("Tạo 3 loại item (Electronics/Art/Vehicle)")
    void create_all_item_types() {
        Item e = ItemFactory.createItem(ItemType.ELECTRONICS,
                UUID.randomUUID().toString(), "seller-1", "Phone", "d", 100L);
        Item a = ItemFactory.createItem(ItemType.ART,
                UUID.randomUUID().toString(), "seller-1", "Painting", "d", 200L);
        Item v = ItemFactory.createItem(ItemType.VEHICLE,
                UUID.randomUUID().toString(), "seller-1", "Bike", "d", 300L);

        itemDao.save(e);
        itemDao.save(a);
        itemDao.save(v);

        assertEquals(3, itemDao.findAll().size());
    }

    @Test
    @DisplayName("findBySellerId lọc đúng sản phẩm của seller")
    void findBySellerId_filters() {
        itemDao.save(ItemFactory.createItem(ItemType.ART,
                UUID.randomUUID().toString(), "seller-A", "A", "d", 1L));
        itemDao.save(ItemFactory.createItem(ItemType.ART,
                UUID.randomUUID().toString(), "seller-A", "B", "d", 2L));
        itemDao.save(ItemFactory.createItem(ItemType.ART,
                UUID.randomUUID().toString(), "seller-B", "C", "d", 3L));

        assertEquals(2, itemDao.findBySellerId("seller-A").size());
        assertEquals(1, itemDao.findBySellerId("seller-B").size());
    }

    @Test
    @DisplayName("Delete item")
    void delete_item() {
        String itemId = UUID.randomUUID().toString();
        itemDao.save(ItemFactory.createItem(
                ItemType.ART, itemId, "seller", "X", "d", 1L));
        itemDao.deleteById(itemId);
        assertTrue(itemDao.findById(itemId).isEmpty());
    }

    @Test
    @DisplayName("UPSERT cập nhật item đã tồn tại")
    void upsert_updates_existing() {
        String itemId = UUID.randomUUID().toString();
        itemDao.save(ItemFactory.createItem(ItemType.ART, itemId, "s", "Old", "old", 100L));
        itemDao.save(ItemFactory.createItem(ItemType.ART, itemId, "s", "New", "new", 200L));

        Item found = itemDao.findById(itemId).orElseThrow();
        assertEquals("New", found.getName());
        assertEquals(200L, found.getStartPrice());
        assertEquals(1, itemDao.findAll().size());
    }

    // ===== FAKE DAO =====
    static class FakeItemDao implements ItemDao {
        private final Map<String, Item> items = new HashMap<>();

        @Override
        public void save(Item item) {
            items.put(item.getId(), item);
        }

        @Override
        public Optional<Item> findById(String id) {
            return id == null ? Optional.empty() : Optional.ofNullable(items.get(id));
        }

        @Override
        public List<Item> findAll() {
            return new ArrayList<>(items.values());
        }

        @Override
        public List<Item> findBySellerId(String sellerId) {
            return items.values().stream()
                    .filter(i -> i.getSellerId().equals(sellerId))
                    .collect(Collectors.toList());
        }

        @Override
        public void deleteById(String id) {
            items.remove(id);
        }
    }
}