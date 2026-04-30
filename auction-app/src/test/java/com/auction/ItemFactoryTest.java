package com.auction;

import com.auction.shared.model.ArtItem;
import com.auction.shared.model.ElectronicsItem;
import com.auction.shared.model.Item;
import com.auction.shared.model.ItemType;
import com.auction.shared.model.VehicleItem;
import com.auction.shared.pattern.ItemFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ItemFactoryTest {

    @Test
    void shouldCreateCorrectItemSubclass() {
        Item electronics = ItemFactory.createItem(ItemType.ELECTRONICS, "I1", "S1", "Laptop", "Dell", 1000);
        Item art = ItemFactory.createItem(ItemType.ART, "I2", "S2", "Painting", "Oil", 2000);
        Item vehicle = ItemFactory.createItem(ItemType.VEHICLE, "I3", "S3", "Car", "Used", 3000);

        assertInstanceOf(ElectronicsItem.class, electronics);
        assertInstanceOf(ArtItem.class, art);
        assertInstanceOf(VehicleItem.class, vehicle);
    }
}