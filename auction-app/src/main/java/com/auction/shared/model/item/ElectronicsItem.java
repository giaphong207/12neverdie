package com.auction.shared.model.item;

public final class ElectronicsItem extends Item {
    // Bước giá đề xuất: 5% giá khởi điểm, LÀM TRÒN LÊN bội số 10.000đ
    // (điện tử thường niêm yết giá tròn 10k → bước giá cũng nên tròn 10k)
    @Override
    public long suggestedMinIncrement(long currentPrice) {
        long base = Math.max(1L, Math.round(currentPrice * 0.05));
        return Math.max(10_000L, ((base + 9_999L) / 10_000L) * 10_000L);
    }
    public ElectronicsItem(String id, String sellerId, String name, String description, long startPrice) {
        super(id, sellerId, name, description, startPrice);
    }
}