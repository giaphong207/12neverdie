package com.auction.shared.model.bid;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Cấu hình Auto-Bid của một bidder cho một auction cụ thể.
 *
 * Lưu ý: KHÔNG broadcast object này ra ngoài client (Quy tắc 3 tuần 5),
 * vì sẽ lộ maxAmount của bidder khác.
 *
 * Khác Bid/Auction của TV2: class này MUTABLE để có thể disable/enable
 * và update maxAmount/increment khi user gọi upsertConfig() lần nữa.
 * Vẫn an toàn vì AutoBidConfig chỉ được sửa ở server-side qua AutoBidService.
 */
public class AutoBidConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String auctionId;
    private final String bidderId;
    private long maxAmount;
    private long increment;
    private boolean enabled;
    private final LocalDateTime createdAt;

    public AutoBidConfig(String id,
                         String auctionId,
                         String bidderId,
                         long maxAmount,
                         long increment) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("AutoBidConfig id không được rỗng");
        }
        if (auctionId == null || auctionId.isBlank()) {
            throw new IllegalArgumentException("Auction id không được rỗng");
        }
        if (bidderId == null || bidderId.isBlank()) {
            throw new IllegalArgumentException("Bidder id không được rỗng");
        }
        if (maxAmount <= 0) {
            throw new IllegalArgumentException("MaxAmount phải lớn hơn 0");
        }
        if (increment <= 0) {
            throw new IllegalArgumentException("Increment phải lớn hơn 0");
        }
        this.id = id;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.maxAmount = maxAmount;
        this.increment = increment;
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
    }
    AutoBidConfig(String id,
                  String auctionId,
                  String bidderId,
                  long maxAmount,
                  long increment,
                  boolean enabled,
                  LocalDateTime createdAt) {
        this.id = id;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.maxAmount = maxAmount;
        this.increment = increment;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }
    /**
     * Rule (theo phân công tuần 5):
     *   canOutbid = enabled
     *            && bidderId != currentHighestBidderId
     *            && maxAmount >= currentPrice + increment
     */
    public boolean canOutbid(long currentPrice, String currentHighestBidderId, long minIncrement) {
        if (!enabled) return false;
        if (bidderId.equals(currentHighestBidderId)) return false;
        long step = Math.max(this.increment, minIncrement);
        return maxAmount >= currentPrice + step;
    }

    public long calculateNextAmount(long currentPrice, long runnerUpMaxAmount, long minIncrement) {
        long step = Math.max(this.increment, minIncrement);
        long target = Math.max(currentPrice + step, runnerUpMaxAmount + step);
        return Math.min(target, this.maxAmount);
    }

    public void disable() { this.enabled = false; }
    public void enable()  { this.enabled = true; }

    public void updateMaxAmount(long newMax) {
        if (newMax <= 0) {
            throw new IllegalArgumentException("MaxAmount phải lớn hơn 0");
        }
        this.maxAmount = newMax;
    }

    public void updateIncrement(long newIncrement) {
        if (newIncrement <= 0) {
            throw new IllegalArgumentException("Increment phải lớn hơn 0");
        }
        this.increment = newIncrement;
    }

    public String getId()          { return id; }
    public String getAuctionId()   { return auctionId; }
    public String getBidderId()    { return bidderId; }
    public long getMaxAmount()     { return maxAmount; }
    public long getIncrement()     { return increment; }
    public boolean isEnabled()     { return enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AutoBidConfig that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "AutoBidConfig{id='" + id +
                "', auctionId='" + auctionId +
                "', bidderId='" + bidderId +
                "', maxAmount=" + maxAmount +
                ", increment=" + increment +
                ", enabled=" + enabled + "}";
    }
}