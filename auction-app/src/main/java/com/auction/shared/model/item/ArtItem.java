package com.auction.shared.model.item;

public final class ArtItem extends Item {
    // Bước giá đề xuất: 5% giá khởi điểm, KHÔNG làm tròn
    // (tranh/đồ nghệ thuật có giá lẻ tùy nguồn gốc → giữ giá chính xác 5%)
    @Override
    public long suggestedMinIncrement(long currentPrice) {
        return Math.max(1L, Math.round(currentPrice * 0.05));
    }
    public ArtItem(String id, String sellerId, String name, String description, long startPrice) {
        super(id, sellerId, name, description, startPrice);
    }
}