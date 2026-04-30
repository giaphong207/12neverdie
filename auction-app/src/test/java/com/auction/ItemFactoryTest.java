package com.auction;

import com.auction.shared.model.ArtItem;
import com.auction.shared.model.ElectronicsItem;
import com.auction.shared.model.Item;
import com.auction.shared.model.ItemType;
import com.auction.shared.model.VehicleItem;
import com.auction.shared.pattern.ItemFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.auction.shared.exception.InvalidItemException;

class ItemFactoryTest {

    @Test
    void shouldCreateCorrectItemSubclass() {
        Item electronics = ItemFactory.createItem(
                ItemType.ELECTRONICS,
                "I001",
                "S001",
                "Laptop Dell",
                "Gaming laptop",
                20000000L
        );

        Item art = ItemFactory.createItem(
                ItemType.ART,
                "I002",
                "S002",
                "Buc tranh son dau",
                "Tranh ve tay",
                5000000L
        );

        Item vehicle = ItemFactory.createItem(
                ItemType.VEHICLE,
                "I003",
                "S003",
                "Honda Vision",
                "Xe may cu",
                25000000L
        );

        assertInstanceOf(ElectronicsItem.class, electronics);
        assertInstanceOf(ArtItem.class, art);
        assertInstanceOf(VehicleItem.class, vehicle);
    }

    @Test
    void shouldCreateItemWithCorrectBasicData() {
        Item item = ItemFactory.createItem(
                ItemType.ELECTRONICS,
                "I100",
                "S100",
                "iPhone 15",
                "Dien thoai moi",
                15000000L
        );

        assertEquals("I100", item.getId());
        assertEquals("S100", item.getSellerId());
        assertEquals("iPhone 15", item.getName());
        assertEquals("Dien thoai moi", item.getDescription());
        assertEquals(15000000L, item.getStartPrice());
        assertEquals(ItemType.ELECTRONICS, item.getType());
    }
    @Test
    void shouldThrowExceptionWhenItemTypeIsNull() {
        assertThrows(InvalidItemException.class, () -> {
            ItemFactory.createItem(
                    null,
                    "I001",
                    "S001",
                    "Laptop Dell",
                    "Gaming laptop",
                    20_000_000L
            );
        });
    }

    @Test
    void shouldThrowExceptionWhenItemNameIsEmpty() {
        assertThrows(InvalidItemException.class, () -> {
            ItemFactory.createItem(
                    ItemType.ELECTRONICS,
                    "I001",
                    "S001",
                    "",
                    "Gaming laptop",
                    20_000_000L
            );
        });
    }

    @Test
    void shouldThrowExceptionWhenDescriptionIsEmpty() {
        assertThrows(InvalidItemException.class, () -> {
            ItemFactory.createItem(
                    ItemType.ELECTRONICS,
                    "I001",
                    "S001",
                    "Laptop Dell",
                    "",
                    20_000_000L
            );
        });
    }

    @Test
    void shouldThrowExceptionWhenStartPriceIsInvalid() {
        assertThrows(InvalidItemException.class, () -> {
            ItemFactory.createItem(
                    ItemType.ELECTRONICS,
                    "I001",
                    "S001",
                    "Laptop Dell",
                    "Gaming laptop",
                    0L
            );
        });
    }

    @Test
    void shouldThrowExceptionWhenSellerIdIsEmpty() {
        assertThrows(InvalidItemException.class, () -> {
            ItemFactory.createItem(
                    ItemType.ELECTRONICS,
                    "I001",
                    "",
                    "Laptop Dell",
                    "Gaming laptop",
                    20_000_000L
            );
        });
    }

    @Test
    void shouldThrowExceptionWhenItemIdIsEmpty() {
        assertThrows(InvalidItemException.class, () -> {
            ItemFactory.createItem(
                    ItemType.ELECTRONICS,
                    "",
                    "S001",
                    "Laptop Dell",
                    "Gaming laptop",
                    20_000_000L
            );
        });
    }
    @Test
    void shouldCreateItemSuccessfully() {
        Item item = ItemFactory.createItem(
                ItemType.ELECTRONICS,
                "item-1",
                "seller-1",
                "Laptop",
                "Laptop gaming",
                1000
        );

        assertNotNull(item);
        assertEquals("item-1", item.getId());
        assertEquals("seller-1", item.getSellerId());
        assertEquals("Laptop", item.getName());
        assertEquals("Laptop gaming", item.getDescription());
        assertEquals(1000, item.getStartPrice());
        assertEquals(ItemType.ELECTRONICS, item.getType());
    }


    @Test
    void shouldThrowExceptionWhenItemDescriptionIsEmpty() {
        assertThrows(InvalidItemException.class, () -> {
            ItemFactory.createItem(
                    ItemType.ELECTRONICS,
                    "item-1",
                    "seller-1",
                    "Laptop",
                    "",
                    1000
            );
        });
    }
}
