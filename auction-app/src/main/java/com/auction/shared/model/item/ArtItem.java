package com.auction.shared.model.item;

public final class ArtItem extends Item {
    public ArtItem(String id, String sellerId, String name, String description, long startPrice) {
        super(id, sellerId, name, description, startPrice);
    }
}