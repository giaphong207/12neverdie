package com.auction.shared.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Bid implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String auctionId;
    private final String bidderId;
    private final long amount;
    private final LocalDateTime createdAt;
    private final BidSource source; // tuần 5 thêm

    //Constructor đầy đủ 
    public Bid(String id, String auctionId, String bidderId,
               long amount, LocalDateTime createdAt, BidSource source) {
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
        this.source = source == null ? BidSource.MANUAL : source;
    }

    //Constructor 5 tham số
    public Bid(String id, String auctionId, String bidderId,
               long amount, LocalDateTime createdAt) {
        this(id, auctionId, bidderId, amount, createdAt, BidSource.MANUAL);
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

    public BidSource getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "Bid{"
                + "id='" + id + '\''
                + ", auctionId='" + auctionId + '\''
                + ", bidderId='" + bidderId + '\''
                + ", amount=" + amount
                + ", createdAt=" + createdAt
                + ", source=" + source
                + '}';
    }
}