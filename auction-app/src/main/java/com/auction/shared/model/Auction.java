package com.auction.shared.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Auction implements Serializable {
    private final String id;
    private final String itemId;
    private final String sellerId;
    private long currentPrice;
    private final long minIncrement;
    private AuctionStatus status;
    private final LocalDateTime endTime;
    private String highestBidderId;
    private final List<Bid> bids;

    public Auction(String id,
                   String itemId,
                   String sellerId,
                   long currentPrice,
                   long minIncrement,
                   AuctionStatus status,
                   LocalDateTime endTime) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Phải có ID của Auction");
        }
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("Phải có ID của Item");
        }
        if (sellerId == null || sellerId.isBlank()) {
            throw new IllegalArgumentException("Phải có ID của Seller");
        }
        if (currentPrice < 0) {
            throw new IllegalArgumentException("currentPrice không được âm");
        }
        if (minIncrement <= 0) {
            throw new IllegalArgumentException("minIncrement phải dương");
        }
        if (status == null) {
            throw new IllegalArgumentException("Phải có status của Auction");
        }

        this.id = id;
        this.itemId = itemId;
        this.sellerId = sellerId;
        this.currentPrice = currentPrice;
        this.minIncrement = minIncrement;
        this.status = status;
        this.endTime = endTime;
        this.highestBidderId = null;
        this.bids = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getItemId() {
        return itemId;
    }

    public String getSellerId() {
        return sellerId;
    }

    public long getCurrentPrice() {
        return currentPrice;
    }

    public long getMinIncrement() {
        return minIncrement;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public String getHighestBidderId() {
        return highestBidderId;
    }

    public List<Bid> getBids() {
        return Collections.unmodifiableList(bids);
    }

    public boolean canBid(long amount) {
        if (status != AuctionStatus.RUNNING) {
            return false;
        }
        return amount >= currentPrice + minIncrement;
    }

    public void addBid(Bid bid) {
        if (bid == null) {
            throw new IllegalArgumentException("Phải có Bid");
        }
        if (!id.equals(bid.getAuctionId())) {
            throw new IllegalArgumentException("Bid không thuộc Auction này");
        }
        if (!canBid(bid.getAmount())) {
            throw new IllegalArgumentException("Số tiền Bid không hợp lệ");
        }

        bids.add(bid);
        currentPrice = bid.getAmount();
        highestBidderId = bid.getBidderId();
    }

    public void start() {
        if (status != AuctionStatus.OPEN) {
            throw new IllegalStateException("Auction đang ở trạng thái OPEN mới có thể Start");
        }
        status = AuctionStatus.RUNNING;
    }

    public void finish() {
        if (status != AuctionStatus.RUNNING) {
            throw new IllegalStateException("Auction đang ở trạng thái RUNNING mới có thể Finish");
        }
        status = AuctionStatus.FINISHED;
    }

    @Override
    public String toString() {
        return "Auction{" +
                "id: " + id + '\'' +
                ", itemId: " + itemId + '\'' +
                ", sellerId: " + sellerId + '\'' +
                ", currentPrice: " + currentPrice +
                ", minIncrement: " + minIncrement +
                ", status: " + status +
                ", endTime: " + endTime +
                ", highestBidderId: " + highestBidderId + '\'' +
                ", bids: " + bids.size() +
                '}';
    }
}