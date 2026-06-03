package com.auction.shared.model.item;

public final class VehicleItem extends Item {
    // Bước giá đề xuất: 5% giá khởi điểm, LÀM TRÒN LÊN bội số 100.000đ
    // (xe giá trị lớn → bước giá nên tròn 100k để tiện đặt giá)
    @Override
    public long suggestedMinIncrement(long currentPrice) {
        long base = Math.max(1L, Math.round(currentPrice * 0.05));
        return Math.max(100_000L, ((base + 99_999L) / 100_000L) * 100_000L);
    }
    public VehicleItem(String id, String sellerId, String name, String description, long startPrice) {
        super(id, sellerId, name, description, startPrice);
    }
}