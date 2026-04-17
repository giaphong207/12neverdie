package com.auction.shared.model;

public class ElectronicsItem extends Item {
    public ElectronicsItem(String id, String sellerId, String name, String description, long startPrice) {
        super(id, sellerId, name, description, startPrice, ItemType.ELECTRONICS);
    }
}