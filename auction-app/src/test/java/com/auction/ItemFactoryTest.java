package com.auction;

import com.auction.shared.model.ArtItem;
import com.auction.shared.model.ElectronicsItem;
import com.auction.shared.model.Item;
import com.auction.shared.model.ItemType;
import com.auction.shared.model.VehicleItem;
import com.auction.shared.pattern.ItemFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
}