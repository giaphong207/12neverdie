package com.auction.shared.model.bid;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class Bid implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String auctionId;
    private final String bidderId;
    private final long amount;
    private final LocalDateTime createdAt;
    private final BidSource source; // tuần 5 thêm

    /** Tên người đặt giá — display field, server enrich trước khi gửi (KHÔNG lưu DB). */
    private String bidderName;

    //Constructor đầy đủ
    public Bid(String id, String auctionId, String bidderId,
               long amount, LocalDateTime createdAt, BidSource source) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Phải có ID của Bid");
        if (auctionId == null || auctionId.isBlank())
            throw new IllegalArgumentException("Phải có ID của Auction");
        if (bidderId == null || bidderId.isBlank())
            throw new IllegalArgumentException("Phải có ID của Bidder");
        if (amount <= 0)
            throw new IllegalArgumentException("Số tiền Bid phải dương");
        if (createdAt == null)
            throw new IllegalArgumentException("Không được không có thời gian tạo Bid");
        if (source == null)
            throw new IllegalArgumentException("Phải chọn loại Bid");

        this.id = id;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
        this.createdAt = createdAt;
        this.source = source;
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

    public String getBidderName() {
        return bidderName;
    }

    public void setBidderName(String bidderName) {
        this.bidderName = bidderName;
    }

    public static Bid createNew(String auctionId, String bidderId,
                         long amount, BidSource source){
        return new Bid("B-" + UUID.randomUUID().toString().substring(0, 8), auctionId,
                       bidderId, amount, LocalDateTime.now(), source);

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
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Bid that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
