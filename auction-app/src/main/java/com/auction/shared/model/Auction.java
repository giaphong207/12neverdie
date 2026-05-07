package com.auction.shared.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Auction implements Serializable {
    private final String id;
    private final String itemId;
    private final String sellerId;

    private final long startPrice;
    private long currentPrice;
    private final long minIncrement;

    private AuctionStatus status;

    private final LocalDateTime startTime;
    private LocalDateTime endTime; // xóa final để anti-sniping có thể gia hạn

    private String highestBidderId;
    private String winnerBidderId;

    private final List<Bid> bidHistory;

    public Auction(String id,
                   String itemId,
                   String sellerId,
                   long startPrice,
                   long minIncrement,
                   AuctionStatus status,
                   LocalDateTime startTime,
                   LocalDateTime endTime) {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Auction id không được rỗng");
        }
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("Item id không được rỗng");
        }
        if (sellerId == null || sellerId.isBlank()) {
            throw new IllegalArgumentException("Seller id không được rỗng");
        }
        if (startPrice < 0) {
            throw new IllegalArgumentException("Start price không được âm");
        }
        if (minIncrement <= 0) {
            throw new IllegalArgumentException("Min increment phải lớn hơn 0");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status không được null");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("Start time không được null");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("End time không được null");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time phải sau start time");
        }

        this.id = id;
        this.itemId = itemId;
        this.sellerId = sellerId;
        this.startPrice = startPrice;
        this.currentPrice = startPrice;
        this.minIncrement = minIncrement;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.highestBidderId = null;
        this.winnerBidderId = null;
        this.bidHistory = new ArrayList<>();
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

    public long getStartPrice() {
        return startPrice;
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

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public String getHighestBidderId() {
        return highestBidderId;
    }

    public String getWinnerBidderId() {
        return winnerBidderId;
    }

    public List<Bid> getBidHistory() {
        return Collections.unmodifiableList(bidHistory);
    }

    public boolean isRunning() {
        return status == AuctionStatus.RUNNING;
    }

    public boolean isFinished() {
        return status == AuctionStatus.FINISHED;
    }

    public boolean hasExpired(LocalDateTime now) {
        return !now.isBefore(endTime);
    }

    public boolean canAcceptBid(long amount) {
        return status == AuctionStatus.RUNNING
                && amount >= currentPrice + minIncrement;
    }

    public void addBid(Bid bid) {
        if (bid == null) {
            throw new IllegalArgumentException("Bid không được null");
        }
        if (!id.equals(bid.getAuctionId())) {
            throw new IllegalArgumentException("Bid không thuộc auction này");
        }
        if (!canAcceptBid(bid.getAmount())) {
            throw new IllegalArgumentException("Giá bid không hợp lệ");
        }

        bidHistory.add(bid);
        currentPrice = bid.getAmount();
        highestBidderId = bid.getBidderId();
    }

    public void updateStatusByTime(LocalDateTime now) {
        if (now.isBefore(startTime)) {
            status = AuctionStatus.OPEN;
            return;
        }

        if (!now.isBefore(startTime) && now.isBefore(endTime)) {
            status = AuctionStatus.RUNNING;
            return;
        }

        if (!now.isBefore(endTime)) {
            finish();
        }
    }

    public void finish() {
        status = AuctionStatus.FINISHED;
        winnerBidderId = highestBidderId;
    }

    public Optional<String> determineWinnerId() {
        return Optional.ofNullable(winnerBidderId);
    }

    @Override
    public String toString() {
        return "Auction{" +
                "id='" + id + '\'' +
                ", itemId='" + itemId + '\'' +
                ", sellerId='" + sellerId + '\'' +
                ", startPrice=" + startPrice +
                ", currentPrice=" + currentPrice +
                ", minIncrement=" + minIncrement +
                ", status=" + status +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", highestBidderId='" + highestBidderId + '\'' +
                ", winnerBidderId='" + winnerBidderId + '\'' +
                '}';
    }
    /**
     * Gia hạn thời gian kết thúc phiên đấu giá.
     * Chỉ được gọi bởi AntiSnipingService khi có bid ở giây cuối.
     *
     * @param seconds số giây muốn cộng thêm (thường là 60)
     */
    public void extendEndTime(long seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("Số giây gia hạn phải dương");
        }
        this.endTime = this.endTime.plusSeconds(seconds);
    }
}