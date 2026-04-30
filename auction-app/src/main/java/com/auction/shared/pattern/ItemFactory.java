package com.auction.shared.pattern;
import com.auction.shared.exception.InvalidItemException;
import com.auction.shared.model.*;

public final class ItemFactory {
    public static Item createItem(
            ItemType type,
            String id,
            String sellerId,
            String name,
            String description,
            long startPrice)
    {
        if (type == null) {
            throw new InvalidItemException("Loại sản phẩm không được để trống");
        }

        if (name == null || name.isBlank()) {
            throw new InvalidItemException("Tên sản phẩm không được để trống");
        }

        if (startPrice <= 0) {
            throw new InvalidItemException("Giá khởi điểm phải lớn hơn 0");
        }
        return switch (type) {
            case ELECTRONICS -> new ElectronicsItem(id, sellerId, name, description, startPrice);
            case ART -> new ArtItem(id, sellerId, name, description, startPrice);
            case VEHICLE -> new VehicleItem(id, sellerId, name, description, startPrice);
        };
    }
}