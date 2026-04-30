package com.auction.server.service;

import com.auction.server.dao.ItemDao;
import com.auction.shared.exception.InvalidItemException;
import com.auction.shared.exception.ItemNotFoundException;
import com.auction.shared.model.Item;
import com.auction.shared.pattern.ItemFactory;
import com.auction.shared.model.ItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ItemServiceTest {

    private ItemService itemService;
    private FakeItemDao itemDao;

    @BeforeEach
    void setUp() {
        itemDao = new FakeItemDao();
        itemService = new DefaultItemService(itemDao);
    }

    @Test
    void shouldAddItemSuccessfully() {
        Item item = createValidItem("item-1");

        itemService.addItem(item);

        assertTrue(itemDao.findById("item-1").isPresent());
        assertEquals(1, itemDao.findBySellerId("seller-1").size());
    }

    @Test
    void shouldUpdateItemSuccessfully() {
        Item oldItem = createValidItem("item-1");
        itemService.addItem(oldItem);

        Item updatedItem = ItemFactory.createItem(
                ItemType.ELECTRONICS,
                "item-1",
                "seller-1",
                "Laptop mới",
                "Laptop đã cập nhật",
                2000
        );

        itemService.updateItem(updatedItem);

        Item savedItem = itemDao.findById("item-1").orElseThrow();

        assertEquals("Laptop mới", savedItem.getName());
        assertEquals("Laptop đã cập nhật", savedItem.getDescription());
        assertEquals(2000, savedItem.getStartPrice());
    }

    @Test
    void shouldDeleteItemSuccessfully() {
        Item item = createValidItem("item-1");
        itemService.addItem(item);

        itemService.deleteItem("item-1");

        assertTrue(itemDao.findById("item-1").isEmpty());
    }

    @Test
    void shouldGetItemsBySellerSuccessfully() {
        Item item1 = createValidItem("item-1");

        Item item2 = ItemFactory.createItem(
                ItemType.ELECTRONICS,
                "item-2",
                "seller-1",
                "Phone",
                "Smartphone",
                500
        );

        Item item3 = ItemFactory.createItem(
                ItemType.ELECTRONICS,
                "item-3",
                "seller-2",
                "Tablet",
                "Tablet Android",
                700
        );

        itemService.addItem(item1);
        itemService.addItem(item2);
        itemService.addItem(item3);

        List<Item> sellerItems = itemService.getItemsBySeller("seller-1");

        assertEquals(2, sellerItems.size());
        assertTrue(sellerItems.stream().allMatch(item -> item.getSellerId().equals("seller-1")));
    }

    @Test
    void shouldThrowExceptionWhenItemAlreadyExists() {
        Item item = createValidItem("item-1");

        itemService.addItem(item);

        assertThrows(InvalidItemException.class, () -> {
            itemService.addItem(item);
        });
    }

    @Test
    void shouldThrowExceptionWhenAddItemIsNull() {
        assertThrows(InvalidItemException.class, () -> {
            itemService.addItem(null);
        });
    }

    @Test
    void shouldThrowExceptionWhenUpdateItemDoesNotExist() {
        Item item = createValidItem("item-not-exist");

        assertThrows(ItemNotFoundException.class, () -> {
            itemService.updateItem(item);
        });
    }

    @Test
    void shouldThrowExceptionWhenUpdateItemIsNull() {
        assertThrows(InvalidItemException.class, () -> {
            itemService.updateItem(null);
        });
    }

    @Test
    void shouldThrowExceptionWhenDeleteItemDoesNotExist() {
        assertThrows(ItemNotFoundException.class, () -> {
            itemService.deleteItem("item-not-exist");
        });
    }

    @Test
    void shouldThrowExceptionWhenDeleteItemIdIsEmpty() {
        assertThrows(InvalidItemException.class, () -> {
            itemService.deleteItem("");
        });
    }

    @Test
    void shouldThrowExceptionWhenSellerIdIsEmpty() {
        assertThrows(InvalidItemException.class, () -> {
            itemService.getItemsBySeller("");
        });
    }

    private Item createValidItem(String id) {
        return ItemFactory.createItem(
                ItemType.ELECTRONICS,
                id,
                "seller-1",
                "Laptop",
                "Laptop gaming",
                1000
        );
    }

    private static class FakeItemDao implements ItemDao {

        private final Map<String, Item> items = new HashMap<>();
        @Override
        public List<Item> findAll() {
            return new ArrayList<>(items.values());
        }

        @Override
        public Optional<Item> findById(String id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public List<Item> findBySellerId(String sellerId) {
            List<Item> result = new ArrayList<>();

            for (Item item : items.values()) {
                if (Objects.equals(item.getSellerId(), sellerId)) {
                    result.add(item);
                }
            }

            return result;
        }

        @Override
        public void save(Item item) {
            items.put(item.getId(), item);
        }

        @Override
        public void deleteById(String id) {
            items.remove(id);
        }
    }
}
