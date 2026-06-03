package com.auction.shared.factory;

import com.auction.shared.exception.AppExceptions.InvalidItemException;
import com.auction.shared.model.item.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
        assertEquals(ItemType.ELECTRONICS, ItemFactory.toItemType(item));
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
        assertEquals(ItemType.ELECTRONICS, ItemFactory.toItemType(item));
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
    @Test
    void suggestedMinIncrement_giaTronThi3LoaiTraSame() {
        // Khi startPrice là số tròn, 3 chiến lược làm tròn đều trả về cùng giá trị
        // — đây là trường hợp đơn giản, kết quả = 5% chính xác.
        Item dienTu    = new ElectronicsItem("e1", "s1", "Laptop", "mô tả", 5_000_000L);
        Item ngheThuat = new ArtItem("a1", "s1", "Tranh", "mô tả", 5_000_000L);
        Item xe        = new VehicleItem("v1", "s1", "Xe máy", "mô tả", 5_000_000L);

        assertEquals(500_000L, dienTu.suggestedMinIncrement(10_000_000L));   // 5% = 500k đã tròn 10k
        assertEquals(500_000L, ngheThuat.suggestedMinIncrement(10_000_000L));// 5% không làm tròn
        assertEquals(500_000L, xe.suggestedMinIncrement(10_000_000L));       // 5% = 500k đã tròn 100k
    }

    @Test
    void suggestedMinIncrement_giaLeChoThayChienLuocLamTronKhacNhau() {
        // Khi startPrice là số lẻ, mỗi loại làm tròn khác nhau:
        //   1.234.567 × 5% = 61.728
        //   ElectronicsItem: làm tròn LÊN bội số 10k  → 70.000
        //   ArtItem:         giữ nguyên               → 61.728
        //   VehicleItem:     làm tròn LÊN bội số 100k → 100.000
        Item dienTu    = new ElectronicsItem("e1", "s1", "Laptop", "mô tả", 1_234_567L);
        Item ngheThuat = new ArtItem("a1", "s1", "Tranh", "mô tả", 1_234_567L);
        Item xe        = new VehicleItem("v1", "s1", "Xe máy", "mô tả", 1_234_567L);

        assertEquals( 70_000L, dienTu.suggestedMinIncrement(1_234_567L));
        assertEquals( 61_728L, ngheThuat.suggestedMinIncrement(1_234_567L));
        assertEquals(100_000L, xe.suggestedMinIncrement(1_234_567L));
    }
}
