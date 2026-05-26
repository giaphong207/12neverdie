package com.auction.shared.model.item;

import com.auction.shared.exception.AppExceptions.InvalidItemException;

import java.io.Serializable;

public sealed abstract class Item implements Serializable permits ElectronicsItem, ArtItem, VehicleItem {
    private String id;
    private String sellerId;
    private String name;
    private String description;
    private long startPrice;

    protected Item(String id, String sellerId, String name, String description, long startPrice) {
        if (id == null || id.isBlank()) {
            throw new InvalidItemException("Phải có ID");
        }

        if (sellerId == null || sellerId.isBlank()) {
            throw new InvalidItemException("Phải có ID của seller");
        }

        if (name == null || name.isBlank()) {
            throw new InvalidItemException("Tên sản phẩm không được để trống");
        }

        if (description == null || description.isBlank()) {
            throw new InvalidItemException("Mô tả sản phẩm không được để trống");
        }

        if (startPrice < 0) {
            throw new InvalidItemException("Giá khởi điểm không được âm");
        }

        this.id = id;
        this.sellerId = sellerId;
        this.name = name;
        this.description = description;
        this.startPrice = startPrice;
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

    public void updateBasicInfo(String name, String description, long startPrice) {
        if (name == null || name.isBlank()) {
            throw new InvalidItemException("Tên sản phẩm không được để trống");
        }

        if (description == null || description.isBlank()) {
            throw new InvalidItemException("Mô tả sản phẩm không được để trống");
        }

        if (startPrice < 0) {
            throw new InvalidItemException("Giá khởi điểm không được âm");
        }

        this.name = name;
        this.description = description;
        this.startPrice = startPrice;
    }

    @Override
    public String toString() {
        return "Item{" +
                "ID: '" + id + '\'' +
                ", SellerId: '" + sellerId + '\'' +
                ", Name: '" + name + '\'' +
                ", Description: '" + description + '\'' +
                ", StartPrice: " + startPrice +
                '}';
    }
}