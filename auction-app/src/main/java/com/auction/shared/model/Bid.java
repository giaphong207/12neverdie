package com.auction.shared.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Bid implements Serializable {
    private final String id;
    private final String auctionId;
    private final String bidderId;
    private final long amount;
    private final LocalDateTime createdAt;

    public Bid(String id, String auctionId, String bidderId, long amount, LocalDateTime createdAt) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Phải có ID của Bid");
        }
        if (auctionId == null || auctionId.isBlank()) {
            throw new IllegalArgumentException("Phải có ID của Auction");
        }
        if (bidderId == null || bidderId.isBlank()) {
            throw new IllegalArgumentException("Phải có ID của Bidder");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền Bid phải dương");
        }

        this.id = id;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    public String getId() {
        return id;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public String getBidderId() {
        return bidderId;
    }

    public long getAmount() {
        return amount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}