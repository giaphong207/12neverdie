package com.auction.support;
 
import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;
import com.auction.shared.model.AutoBidConfig;
import com.auction.shared.model.Bid;
import com.auction.shared.model.BidSource;
import com.auction.shared.model.Bidder;
import com.auction.shared.model.User;
 
import java.time.LocalDateTime;
import java.util.UUID;

public final class TestDataFactory {
 
    private TestDataFactory() {}
 
    //Auction
    /**
     * Tạo auction đang RUNNING với giá và thời gian chỉ định.
     *
     * @param currentPrice   giá hiện tại
     * @param minIncrement   bước giá tối thiểu
     * @param secondsUntilEnd số giây còn lại đến hết phiên
     */
    public static Auction runningAuction(long currentPrice,
                                          long minIncrement,
                                          long secondsUntilEnd) {
        LocalDateTime now = LocalDateTime.now();
        Auction auction = new Auction(
                UUID.randomUUID().toString(),
                "item-test-" + UUID.randomUUID().toString().substring(0, 8),
                "seller-test-01",
                currentPrice,
                minIncrement,
                now.minusHours(1),
                now.plusSeconds(secondsUntilEnd)
        );
        auction.setStatus(AuctionStatus.RUNNING);
        return auction;
    }
 
    //Tạo auction đã FINISHED — dùng để test reject bid
    public static Auction finishedAuction() {
        LocalDateTime past = LocalDateTime.now().minusMinutes(5);
        Auction auction = new Auction(
                UUID.randomUUID().toString(),
                "item-finished-01",
                "seller-test-01",
                10_000_000L,
                100_000L,
                past.minusHours(2),
                past
        );
        auction.setStatus(AuctionStatus.FINISHED);
        return auction;
    }
 
    /**
     * Tạo auction RUNNING sắp hết giờ — dùng để test anti-sniping.
     * endTime = now + 20s (trong trigger window 30s).
     */
    public static Auction auctionAboutToEnd() {
        return runningAuction(5_000_000L, 100_000L, 20);
    }
 
    //Tạo auction RUNNING còn nhiều thời gian — anti-sniping KHÔNG trigger
    public static Auction auctionWithPlentyOfTime() {
        return runningAuction(5_000_000L, 100_000L, 300);
    }
 
    //Bid
    //Tạo Bid MANUAL với timestamp hiện tại
    public static Bid bid(String auctionId, String bidderId, long amount) {
        return new Bid(
                UUID.randomUUID().toString(),
                auctionId,
                bidderId,
                amount,
                LocalDateTime.now(),
                BidSource.MANUAL
        );
    }
 
    //Tạo Bid AUTO
    public static Bid autoBid(String auctionId, String bidderId, long amount) {
        return new Bid(
                UUID.randomUUID().toString(),
                auctionId,
                bidderId,
                amount,
                LocalDateTime.now(),
                BidSource.AUTO
        );
    }
 
    //AutoBidConfig
    /**
     * Tạo AutoBidConfig enabled.
     *
     * @param auctionId  id phiên
     * @param bidderId   id bidder
     * @param maxAmount  giá trần
     * @param increment  bước tăng
     */
    public static AutoBidConfig autoBidConfig(String auctionId, String bidderId,
                                               long maxAmount, long increment) {
        return new AutoBidConfig(auctionId, bidderId, maxAmount, increment);
    }
 
    //User
    //Tạo Bidder đơn giản để test
    public static User bidder(String id, String username) {
        return new Bidder(id, username, "testpass");
    }
}