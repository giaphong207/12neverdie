package com.auction.shared.factory;
import com.auction.shared.exception.AppExceptions.InvalidItemException;
import com.auction.shared.model.item.ArtItem;
import com.auction.shared.model.item.ElectronicsItem;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.item.ItemType;
import com.auction.shared.model.item.VehicleItem;

public class ItemFactory {
    public static Item createItem(
            ItemType itemType,
            String id,
            String sellerId,
            String name,
            String description,
            long startPrice){
        if (itemType == null){
            throw new InvalidItemException("Phải điền loại sản phẩm");
        }
        if (id == null || id.isBlank()){
            throw new InvalidItemException("Phải điền ID của sản phẩm");
        }
        if (name == null || name.isBlank()){
            throw new InvalidItemException("Phải điền tên sản phẩm");
        }
        if (description == null || description.isBlank()){
            throw new InvalidItemException("Phải điền mô tả sản phẩm");
        }if (startPrice <= 0){
            throw new InvalidItemException("Giá khởi điểm phải lớn hơn 0");
        }
        return itemType.create(id, sellerId, name, description, startPrice);
    }
    public static ItemType toItemType(Item item) {
        if (item instanceof ElectronicsItem) return ItemType.ELECTRONICS;
        if (item instanceof ArtItem)         return ItemType.ART;
        if (item instanceof VehicleItem)     return ItemType.VEHICLE;
        throw new IllegalStateException("Loại item không xác định");
    }
}