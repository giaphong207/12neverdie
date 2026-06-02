package com.auction.shared.model.item;

public enum ItemType {
    ELECTRONICS {
        @Override
        public Item create(String id, String sellerId, String name,
                           String description, long startPrice) {
            return new ElectronicsItem(id, sellerId, name, description, startPrice);
        }
    },
    ART {
        @Override
        public Item create(String id, String sellerId, String name,
                           String description, long startPrice) {
            return new ArtItem(id, sellerId, name, description, startPrice);
        }
    },
    VEHICLE {
        @Override
        public Item create(String id, String sellerId, String name,
                           String description, long startPrice) {
            return new VehicleItem(id, sellerId, name, description, startPrice);
        }
    };

    /** Factory Method: mỗi loại tự quyết định khởi tạo lớp Item nào (thay cho switch). */
    public abstract Item create(String id, String sellerId, String name,
                                String description, long startPrice);
}