package com.auction.shared.model.item;

public final class VehicleItem extends Item {
    // VehicleItem.java — xe cộ giá trị lớn -> bước cố định LỚN
    @Override
    public long suggestedMinIncrement(long currentPrice) {
        return 1_000_000L;
    }
    public VehicleItem(String id, String sellerId, String name, String description, long startPrice) {
        super(id, sellerId, name, description, startPrice);
    }
}