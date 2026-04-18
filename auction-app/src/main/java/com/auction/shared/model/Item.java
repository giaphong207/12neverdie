package com.auction.shared.model;

import java.io.Serializable;

public abstract class Item implements Serializable {
    private String id;
    private String sellerId;
    private String name;
    private String description;
    private long startPrice;
    private category;
    protected Item(String id, String sellerId, String name, String description, long startPrice, ItemType type) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Phải có ID");
        }
        if (sellerId == null || sellerId.isBlank()) {
            throw new IllegalArgumentException("Phải có ID của seller");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tên sản phẩm không được để trống");
        }
        if (startPrice < 0) {
            throw new IllegalArgumentException("Giá khởi điểm không được âm");
        }
        if (type == null) {
            throw new IllegalArgumentException("Phải điển tên loại sản phẩm");
        }

        this.id = id;
        this.sellerId = sellerId;
        this.name = name;
        this.description = description == null ? "" : description;
        this.startPrice = startPrice;
        this.type = type;
    }
    public String getId() {
        return id;
    }

    public String getSellerId() {
        return sellerId;
    }

    public String getName() {
        return name;
    }
    public String getDescription() {
        return description;
    }


    public long getStartPrice() {
        return startPrice;
    }
    public ItemType getType() {
        return type;
    }
    public void updateBasicInfo(String name, String description, long startPrice) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tên sản phẩm không được để trống");
        }
        if (startPrice < 0) {
            throw new IllegalArgumentException("Giá khởi điểm không được âm");
        }

        this.name = name;
        this.description = description == null ? "" : description;
        this.startPrice = startPrice;
    }
    @Override
    public String toString() {
        return "Item{" +
                "ID: " + id + '\'' +
                ", SellerId: " + sellerId + '\'' +
                ", Name: " + name + '\'' +
                ", Description: " + description + '\'' +
                ", StartPrice: " + startPrice +
                ", Type: " + type +
                '}';
    }
}













