package com.auction.shared.model.item;

public final class ElectronicsItem extends Item {
    // ElectronicsItem.java — đồ điện tử giá thấp, mất giá nhanh -> bước cố định NHỎ
    @Override
    public long suggestedMinIncrement(long currentPrice) {
        return 100_000L;
    }
    public ElectronicsItem(String id, String sellerId, String name, String description, long startPrice) {
        super(id, sellerId, name, description, startPrice);
    }
}