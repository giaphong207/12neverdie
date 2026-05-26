package com.auction.server.DAO;

import com.auction.shared.model.auction.AuctionStatus;
import com.auction.shared.model.bid.Bid;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AuctionEntity {

    private String id;
    private String itemId;
    private String sellerId;
    private long startPrice;
    private long currentPrice;
    private long minIncrement;

    private AuctionStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String highestBidderId;   // nullable
    private String winnerBidderId;    // nullable

    private List<Bid> bidHistory = new ArrayList<>();

    public AuctionEntity() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }

    public long getStartPrice() { return startPrice; }
    public void setStartPrice(long startPrice) { this.startPrice = startPrice; }

    public long getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(long currentPrice) { this.currentPrice = currentPrice; }

    public long getMinIncrement() { return minIncrement; }
    public void setMinIncrement(long minIncrement) { this.minIncrement = minIncrement; }

    public AuctionStatus getStatus() { return status; }
    public void setStatus(AuctionStatus status) { this.status = status; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public String getHighestBidderId() { return highestBidderId; }
    public void setHighestBidderId(String highestBidderId) { this.highestBidderId = highestBidderId; }

    public String getWinnerBidderId() { return winnerBidderId; }
    public void setWinnerBidderId(String winnerBidderId) { this.winnerBidderId = winnerBidderId; }

    public List<Bid> getBidHistory() { return bidHistory; }
    public void setBidHistory(List<Bid> bidHistory) {
        this.bidHistory = bidHistory == null ? new ArrayList<>() : bidHistory;
    }
}
