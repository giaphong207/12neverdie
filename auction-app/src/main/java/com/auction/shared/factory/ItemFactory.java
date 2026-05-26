package com.auction.shared.factory;
import com.auction.shared.model.item.*;

public class ItemFactory {
    public static Item createItem(
            ItemType itemType,
            String id,
            String sellerId,
            String name,
            String description,
            long startPrice){
        if (itemType == null){
            throw new IllegalArgumentException("Phải điền loại sản phẩm");
        }
        if (id == null || id.isBlank()){
            throw new IllegalArgumentException("Phải điền ID của sản phẩm");
        }
        if (name == null || name.isBlank()){
            throw new IllegalArgumentException("Phải điền tên sản phẩm");
        }
        if (description == null || description.isBlank()){
            throw new IllegalArgumentException("Phải điền mô tả sản phẩm");
        }if (startPrice < 0){
            throw new IllegalArgumentException("Giá khởi điểm không thể âm");
        }
        return switch (itemType){
            case ELECTRONICS -> new ElectronicsItem(id,sellerId,name,description,0);
            case ART -> new ArtItem(id,sellerId,name,description,0);
            case VEHICLE -> new VehicleItem(id,sellerId,name,description,0);
        };
    }
    public static ItemType toItemType(Item item) {
        if (item instanceof ElectronicsItem) return ItemType.ELECTRONICS;
        if (item instanceof ArtItem)         return ItemType.ART;
        if (item instanceof VehicleItem)     return ItemType.VEHICLE;
        throw new IllegalStateException("Loại item không xác định");
    }
}