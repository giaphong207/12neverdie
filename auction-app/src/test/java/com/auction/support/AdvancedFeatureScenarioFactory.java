package com.auction.support;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.auction.shared.model.Auction;
import com.auction.shared.model.AutoBidConfig;
import com.auction.shared.model.Bid;
import com.auction.shared.model.BidSource;

/**
 * Factory tạo scenario phức tạp nhiều bước cho integration test.
 *
 * <p>TV1 sở hữu. Tạo sẵn "trạng thái thế giới" hoàn chỉnh để test
 * sự tương tác giữa anti-sniping và auto-bid.
 *
 * <p>Dùng trong AdvancedFeatureIntegrationTest.java
 */
public final class AdvancedFeatureScenarioFactory {

    private AdvancedFeatureScenarioFactory() {}

    // ══════════════════════════════════════════════════════════════
    // SCENARIO A — Anti-sniping triggered + Auto-bid cascade
    // ══════════════════════════════════════════════════════════════

    /**
     * Trạng thái: auction còn 20s + bidder-A có auto-bid config.
     *
     * <p>Kịch bản test:
     * <ol>
     *   <li>bidder-B đặt manual bid</li>
     *   <li>Anti-sniping extend endTime thêm 60s</li>
     *   <li>Auto-bid của bidder-A phản ứng</li>
     *   <li>Final: bidder-A dẫn đầu, endTime đã tăng</li>
     * </ol>
     */
    public static ScenarioA createScenarioA() {
        Auction auction = TestDataFactory.auctionAboutToEnd();

        AutoBidConfig configA = TestDataFactory.autoBidConfig(
                auction.getId(), "bidder-A", 7_000_000L, 100_000L);

        return new ScenarioA(auction, configA, "bidder-B", "bidder-A");
    }

    public static final class ScenarioA {
        public final Auction auction;
        public final AutoBidConfig autoBidConfig;
        public final String manualBidderId;
        public final String autoBidderId;

        private ScenarioA(Auction auction, AutoBidConfig config,
                          String manualBidderId, String autoBidderId) {
            this.auction = auction;
            this.autoBidConfig = config;
            this.manualBidderId = manualBidderId;
            this.autoBidderId = autoBidderId;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // SCENARIO B — 2 auto-bidder cạnh tranh
    // ══════════════════════════════════════════════════════════════

    /**
     * Trạng thái: 2 auto-bidder với maxAmount khác nhau.
     *
     * <p>Giá dự đoán:
     * <pre>
     *   manual bid = 5_100_000
     *   auto-A: max 5_500_000, incr 100_000
     *   auto-B: max 6_000_000, incr 100_000
     *   → auto-A dừng ở 5_500_000
     *   → auto-B outbid: 5_600_000
     *   → winner: auto-B
     * </pre>
     */
    public static ScenarioB createScenarioB() {
        Auction auction = TestDataFactory.runningAuction(5_000_000L, 100_000L, 300);

        AutoBidConfig configA = TestDataFactory.autoBidConfig(
                auction.getId(), "auto-A", 5_500_000L, 100_000L);

        AutoBidConfig configB = TestDataFactory.autoBidConfig(
                auction.getId(), "auto-B", 6_000_000L, 100_000L);

        return new ScenarioB(auction, configA, configB,
                "manual-bidder", 5_100_000L, "auto-B", 5_600_000L);
    }

    public static final class ScenarioB {
        public final Auction auction;
        public final AutoBidConfig configA;
        public final AutoBidConfig configB;
        public final String manualBidderId;
        public final long manualBidAmount;
        public final String expectedWinner;
        public final long expectedFinalPrice;

        private ScenarioB(Auction auction, AutoBidConfig configA, AutoBidConfig configB,
                          String manualBidderId, long manualBidAmount,
                          String expectedWinner, long expectedFinalPrice) {
            this.auction = auction;
            this.configA = configA;
            this.configB = configB;
            this.manualBidderId = manualBidderId;
            this.manualBidAmount = manualBidAmount;
            this.expectedWinner = expectedWinner;
            this.expectedFinalPrice = expectedFinalPrice;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // SCENARIO C — Tie-break theo createdAt
    // ══════════════════════════════════════════════════════════════

    /**
     * 2 auto-bidder cùng maxAmount — config tạo sớm hơn thắng.
     */
    public static ScenarioC createScenarioC() {
        Auction auction = TestDataFactory.runningAuction(5_000_000L, 100_000L, 300);
        LocalDateTime now = LocalDateTime.now();

        AutoBidConfig configEarly = TestDataFactory.autoBidConfigCreatedAt(
                auction.getId(), "auto-early",
                6_000_000L, 100_000L,
                now.minusSeconds(10)
        );

        AutoBidConfig configLate = TestDataFactory.autoBidConfigCreatedAt(
                auction.getId(), "auto-late",
                6_000_000L, 100_000L,
                now
        );

        return new ScenarioC(auction, configEarly, configLate, "auto-early");
    }

    public static final class ScenarioC {
        public final Auction auction;
        public final AutoBidConfig configEarly;
        public final AutoBidConfig configLate;
        public final String expectedWinner;

        private ScenarioC(Auction auction, AutoBidConfig configEarly,
                          AutoBidConfig configLate, String expectedWinner) {
            this.auction = auction;
            this.configEarly = configEarly;
            this.configLate = configLate;
            this.expectedWinner = expectedWinner;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // HELPER — Phân tích bidHistory
    // ══════════════════════════════════════════════════════════════

    /**
     * Lấy danh sách amount theo thứ tự từ bidHistory.
     * Dùng để assert giá tăng dần trong test.
     */
    public static List<Long> extractPrices(List<Bid> bidHistory) {
        List<Long> prices = new ArrayList<>();
        for (Bid bid : bidHistory) {
            prices.add(bid.getAmount());
        }
        return prices;
    }

    /**
     * Đếm số bid AUTO trong bidHistory.
     */
    public static long countAutoBids(List<Bid> bidHistory) {
        return bidHistory.stream()
                .filter(b -> b.getSource() == BidSource.AUTO)
                .count();
    }

    /**
     * Đếm số bid MANUAL trong bidHistory.
     */
    public static long countManualBids(List<Bid> bidHistory) {
        return bidHistory.stream()
                .filter(b -> b.getSource() == BidSource.MANUAL)
                .count();
    }
}