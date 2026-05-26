package com.auction.support;

import java.time.LocalDateTime;
import java.util.UUID;

import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;
import com.auction.shared.model.bid.AutoBidConfig;
import com.auction.shared.model.bid.Bid;
import com.auction.shared.model.bid.BidSource;
import com.auction.shared.model.user.Bidder;
import com.auction.shared.model.user.User;

public final class TestDataFactory {

    private TestDataFactory() {}

    // ── Auction ───────────────────────────────────────────────────────────────

    /**
     * Tạo Auction RUNNING với giá và thời gian chỉ định.
     * Auction constructor: (id, itemId, sellerId, startPrice, minIncrement,
     *                       status, startTime, endTime)
     */
    public static Auction runningAuction(long currentPrice,
                                         long minIncrement,
                                         long secondsUntilEnd) {
        LocalDateTime now = LocalDateTime.now();
        return new Auction(
                UUID.randomUUID().toString(),
                "item-test-" + UUID.randomUUID().toString().substring(0, 8),
                "seller-test-01",
                currentPrice,
                minIncrement,
                AuctionStatus.RUNNING,
                now.minusHours(1),
                now.plusSeconds(secondsUntilEnd)
        );
    }

    /** Auction FINISHED — test reject bid. */
    public static Auction finishedAuction() {
        LocalDateTime past = LocalDateTime.now().minusMinutes(5);
        return new Auction(
                UUID.randomUUID().toString(),
                "item-finished-01",
                "seller-test-01",
                10_000_000L,
                100_000L,
                AuctionStatus.FINISHED,
                past.minusHours(2),
                past
        );
    }

    /** Auction RUNNING còn 20s — trigger anti-sniping. */
    public static Auction auctionAboutToEnd() {
        return runningAuction(5_000_000L, 100_000L, 20);
    }

    /** Auction RUNNING còn 300s — anti-sniping KHÔNG trigger. */
    public static Auction auctionWithPlentyOfTime() {
        return runningAuction(5_000_000L, 100_000L, 300);
    }

    // ── Bid ───────────────────────────────────────────────────────────────────

    public static Bid bid(String auctionId, String bidderId, long amount) {
        return Bid.createNew(auctionId, bidderId, amount, BidSource.MANUAL);
    }

    public static Bid autoBid(String auctionId, String bidderId, long amount) {
        return Bid.createNew(auctionId, bidderId, amount, BidSource.AUTO);

    }

    // ── AutoBidConfig ─────────────────────────────────────────────────────────

    /**
     * AutoBidConfig constructor thực tế:
     * (id, auctionId, bidderId, maxAmount, increment)
     */
    public static AutoBidConfig autoBidConfig(String auctionId, String bidderId,
                                               long maxAmount, long increment) {
        return new AutoBidConfig(
                UUID.randomUUID().toString(),
                auctionId,
                bidderId,
                maxAmount,
                increment
        );
    }

    /**
     * AutoBidConfig với createdAt tùy chỉnh — dùng cho tie-break test.
     *
     * AutoBidConfig.createdAt là final nên không có setCreatedAt().
     * Workaround: tạo subclass ẩn danh override getCreatedAt().
     * Chỉ dùng trong test — không dùng trong production code.
     */
    public static AutoBidConfig autoBidConfigCreatedAt(String auctionId, String bidderId,
                                                        long maxAmount, long increment,
                                                        LocalDateTime createdAt) {
        return new AutoBidConfig(
                UUID.randomUUID().toString(),
                auctionId,
                bidderId,
                maxAmount,
                increment
        ) {
            @Override
            public LocalDateTime getCreatedAt() {
                return createdAt;
            }
        };
    }

    // ── User ──────────────────────────────────────────────────────────────────

    /** Bidder đơn giản để test. */
    public static User bidder(String id, String username) {
        return new Bidder(id, username, "testpass");
    }
}