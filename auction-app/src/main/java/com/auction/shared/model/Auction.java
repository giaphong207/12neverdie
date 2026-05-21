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

    // ─────────── Nhóm câu hỏi nghiệp vụ — chỉ đọc, không sửa state ───────────
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
    /**
     * Chuyển auction OPEN → RUNNING.
     * Gọi bởi LifecycleService khi scheduler fire đúng startTime.
     *
     * @throws IllegalStateException nếu auction không ở trạng thái OPEN
     */
    public void start() {
        if (status != AuctionStatus.OPEN) {
            throw new IllegalStateException(
                    "Chỉ có thể start auction đang OPEN, hiện tại: " + status);
        }
        this.status = AuctionStatus.RUNNING;
    }

    /**
     * Chuyển auction RUNNING → FINISHED và chốt người thắng.
     * winnerBidderId được copy từ highestBidderId tại thời điểm này.
     * Nếu chưa có bid nào, winnerBidderId vẫn null (auction không người thắng).
     *
     * @throws IllegalStateException nếu auction không ở trạng thái RUNNING
     */
    public void finish() {
        if (status != AuctionStatus.RUNNING) {
            throw new IllegalStateException(
                    "Chỉ có thể finish auction đang RUNNING, hiện tại: " + status);
        }
        this.status = AuctionStatus.FINISHED;
        this.winnerBidderId = this.highestBidderId;
    }
    /**
     * Hủy auction. Không cho hủy nếu đã FINISHED hoặc PAID.
     *
     * @throws IllegalStateException nếu auction đã kết thúc hoặc đã thanh toán
     */
    public void cancel() {
        if (status == AuctionStatus.FINISHED || status == AuctionStatus.PAID) {
            throw new IllegalStateException(
                    "Không thể hủy auction ở trạng thái: " + status);
        }
        this.status = AuctionStatus.CANCELED;
    }
    /**
     * Đánh dấu auction đã thanh toán.
     *
     * @throws IllegalStateException nếu auction chưa FINISHED
     */
    public void markPaid() {
        if (status != AuctionStatus.FINISHED) {
            throw new IllegalStateException(
                    "Chỉ có thể đánh dấu PAID cho auction đã FINISHED, hiện tại: " + status);
        }
        this.status = AuctionStatus.PAID;
    }
    /**
     * Gia hạn thời gian kết thúc phiên đấu giá (cho anti-sniping).
     *
     * Chỉ áp dụng được khi auction đang RUNNING.
     *
     * ⚠️ LƯU Ý cho caller:
     * Sau khi gọi method này, PHẢI gọi {@code lifecycleService.rescheduleClose(auction)}
     * để scheduler fire đúng theo endTime mới. Nếu không, scheduler vẫn close
     * auction theo endTime cũ.
     *
     * @param seconds số giây muốn cộng thêm (phải dương)
     * @throws IllegalArgumentException nếu seconds <= 0
     * @throws IllegalStateException    nếu auction không ở trạng thái RUNNING
     */
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

    // ── DAO-ONLY (khôi phục từ DB, bypass validation) ──
    // ⚠️ CẢNH BÁO: KHÔNG gọi từ business code (Service, Controller...).
    //   Chỉ JdbcAuctionDao.mapAuction() được phép gọi các method này.
    /**
     * Gán currentPrice + highestBidderId + winnerBidderId trực tiếp.
     * Dùng khi load Auction đang giữa chừng (đã có bid).
     */
    public void restoreState(long currentPrice, String highestBidderId, String winnerBidderId) {
        this.currentPrice = currentPrice;
        this.highestBidderId = highestBidderId;
        this.winnerBidderId = winnerBidderId;
    }

    /**
     * Gán status trực tiếp (bypass logic chuyển trạng thái theo thời gian).
     */
    public void restoreStatus(AuctionStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status không được null");
        }
        this.status = status;
    }
    /**
     * Khôi phục bid history khi load từ DB.
     * KHÔNG re-validate (vì auction có thể đã FINISHED).
     * Chỉ gọi 1 lần ngay sau khi tạo Auction từ ResultSet.
     */
    public void restoreBidHistory(java.util.List<Bid> bids) {
        if (!this.bidHistory.isEmpty()) {
            throw new IllegalStateException("Bid history đã được nạp rồi");
        }
        if (bids != null) {
            this.bidHistory.addAll(bids);
        }
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