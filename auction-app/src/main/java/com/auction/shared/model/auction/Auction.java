package com.auction.shared.model.auction;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.auction.shared.exception.AppExceptions.InvalidBidException;
import com.auction.shared.model.bid.Bid;

public class Auction implements Serializable {
    private final String id;
    private final String itemId;
    private final String sellerId;
    private final long startPrice;
    private final long minIncrement;
    private final LocalDateTime startTime;
    private final List<Bid> bidHistory;

    private AuctionStatus status;
    private long currentPrice;
    private LocalDateTime endTime; // xóa final để anti-sniping có thể gia hạn
    private String highestBidderId;
    private String winnerBidderId;

    // Display fields — server fill trước khi gửi qua wire để client khỏi phải lookup
    private String itemName;
    private String itemDescription;
    private String sellerName;
    private String highestBidderName;
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
        if (startPrice <= 0) {
            throw new IllegalArgumentException("Giá khởi điểm phải lớn hơn 0");
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

    Auction(String id,
            String itemId,
            String sellerId,
            long startPrice,
            long currentPrice,
            long minIncrement,
            AuctionStatus status,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String highestBidderId,
            String winnerBidderId,
            List<Bid> bidHistory){
        this.id = id;
        this.itemId = itemId;
        this.sellerId = sellerId;
        this.startPrice = startPrice;
        this.currentPrice = currentPrice;
        this.minIncrement = minIncrement;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.highestBidderId = highestBidderId;
        this.winnerBidderId = winnerBidderId;
        this.bidHistory = bidHistory;
    }
    // ── QUERY (đọc trạng thái) ──────────────
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

    // ── Display fields (set ở server) ──
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getItemDescription() { return itemDescription; }
    public void setItemDescription(String d) { this.itemDescription = d; }
    public String getSellerName() { return sellerName; }
    public void setSellerName(String n) { this.sellerName = n; }
    public String getHighestBidderName() { return highestBidderName; }
    public void setHighestBidderName(String n) { this.highestBidderName = n; }

    // Nhóm câu hỏi nghiệp vụ — chỉ đọc, không sửa state
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
    public Optional<String> determineWinnerId() { return Optional.ofNullable(winnerBidderId);}

    // ── COMMAND (chuyển trạng thái) ─────────
    public void addBid(Bid bid) {
        if (bid == null) {
            throw new InvalidBidException("Bid không được null");
        }
        if (!id.equals(bid.getAuctionId())) {
            throw new InvalidBidException("Bid không thuộc auction này");
        }
        if (!canAcceptBid(bid.getAmount())) {
            throw new InvalidBidException("Giá bid không hợp lệ");
        }

        bidHistory.add(bid);
        currentPrice = bid.getAmount();
        highestBidderId = bid.getBidderId();
    }
    public void start() {
        if (status != AuctionStatus.OPEN) {
            throw new IllegalStateException(
                    "Chỉ có thể start auction đang OPEN, hiện tại: " + status);
        }
        this.status = AuctionStatus.RUNNING;
    }

    public void finish() {
        if (status != AuctionStatus.RUNNING) {
            throw new IllegalStateException(
                    "Chỉ có thể finish auction đang RUNNING, hiện tại: " + status);
        }
        this.status = AuctionStatus.FINISHED;
        this.winnerBidderId = this.highestBidderId;
    }
    //Hủy auction. Không cho hủy nếu đã FINISHED hoặc PAID.
    public void cancel() {
        if (status == AuctionStatus.FINISHED || status == AuctionStatus.PAID) {
            throw new IllegalStateException(
                    "Không thể hủy auction ở trạng thái: " + status);
        }
        this.status = AuctionStatus.CANCELED;
    }
        public void markPaid() {
        if (status != AuctionStatus.FINISHED) {
            throw new IllegalStateException(
                    "Chỉ có thể đánh dấu PAID cho auction đã FINISHED, hiện tại: " + status);
        }
        this.status = AuctionStatus.PAID;
    }

    public void extendEndTime(long seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("Số giây gia hạn phải dương");
        }
        if (status != AuctionStatus.RUNNING) {
            throw new IllegalStateException(
                    "Chỉ có thể gia hạn auction đang RUNNING, hiện tại: " + status);
        }
        this.endTime = this.endTime.plusSeconds(seconds);
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
}