package com.auction.shared.model;

public class VehicleItem extends Item {
    public VehicleItem(String id, String sellerId, String name, String description, long startPrice) {
        super(id, sellerId, name, description, startPrice, ItemType.VEHICLE);
    }
}