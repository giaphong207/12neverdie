package com.auction.shared.pattern;
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
        return switch (type) {
            case ELECTRONICS -> new ElectronicsItem(id, sellerId, name, description, startPrice);
            case ART -> new ArtItem(id, sellerId, name, description, startPrice);
            case VEHICLE -> new VehicleItem(id, sellerId, name, description, startPrice);
        };
    }
}