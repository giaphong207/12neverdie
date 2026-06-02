package com.auction.shared.model.item;

public final class ArtItem extends Item {
    // ArtItem.java — tranh/đồ nghệ thuật biến động mạnh -> bước theo PHẦN TRĂM giá
    @Override
    public long suggestedMinIncrement(long currentPrice) {
        long byPercent = Math.round(currentPrice * 0.05); // 5% giá hiện tại
        return Math.max(byPercent, 50_000L);              // có sàn để giá thấp không ra bước quá nhỏ
    }
    public ArtItem(String id, String sellerId, String name, String description, long startPrice) {
        super(id, sellerId, name, description, startPrice);
    }
}