package com.auction.server.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.auction.server.dao.AutoBidDao;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AutoBidConfig;
import com.auction.support.AdvancedFeatureScenarioFactory;
import com.auction.support.TestDataFactory;

/**
 * Integration test cho chuỗi xử lý đầy đủ tuần 5.
 */
class AdvancedFeatureIntegrationTest {

    private AntiSnipingService antiSnipingService;
    private AutoBidService autoBidService;
    private InMemoryAutoBidDao autoBidDao;

    @BeforeEach
    void setUp() {
        autoBidDao = new InMemoryAutoBidDao();
        antiSnipingService = new DefaultAntiSnipingService();
        autoBidService = new DefaultAutoBidService(autoBidDao);
    }

    // ══════════════════════════════════════════════════════════════
    // TEST 1 — Anti-sniping extend đúng 60 giây
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Anti-sniping extend đúng 60 giây khi còn <= 30s")
    void antiSniping_shouldExtend60Seconds() {
        Auction auction = TestDataFactory.auctionAboutToEnd();
        LocalDateTime originalEnd = auction.getEndTime();

        // Thêm 1 bid trước để auction có highestBidder
        auction.addBid(TestDataFactory.bid(
                auction.getId(), "bidder-1", 5_100_000L));

        boolean extended = antiSnipingService.applyExtensionIfNeeded(
                auction, LocalDateTime.now());

        assertTrue(extended, "Phải extend khi còn <= 30 giây");
        long extensionSeconds = java.time.temporal.ChronoUnit.SECONDS
                .between(originalEnd, auction.getEndTime());
        assertEquals(60L, extensionSeconds, "Extension phải đúng 60 giây");
    }

    // ══════════════════════════════════════════════════════════════
    // TEST 2 — Anti-sniping không extend khi còn nhiều thời gian
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Anti-sniping không extend khi còn > 30s")
    void antiSniping_shouldNotExtendWhenPlentyOfTime() {
        Auction auction = TestDataFactory.auctionWithPlentyOfTime();
        LocalDateTime originalEnd = auction.getEndTime();

        boolean extended = antiSnipingService.applyExtensionIfNeeded(
                auction, LocalDateTime.now());

        assertFalse(extended, "Không được extend khi còn > 30 giây");
        assertEquals(originalEnd, auction.getEndTime());
    }

    // ══════════════════════════════════════════════════════════════
    // TEST 3 — Anti-sniping chỉ extend 1 lần
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Anti-sniping chỉ extend 1 lần — lần 2 không extend")
    void antiSniping_shouldOnlyExtendOnce() {
        Auction auction = TestDataFactory.auctionAboutToEnd();

        auction.addBid(TestDataFactory.bid(
                auction.getId(), "bidder-1", 5_100_000L));

        boolean extended1 = antiSnipingService.applyExtensionIfNeeded(
                auction, LocalDateTime.now());
        LocalDateTime afterFirst = auction.getEndTime();

        assertTrue(extended1);

        // Sau extend auction còn ~80s → không trigger nữa
        boolean extended2 = antiSnipingService.applyExtensionIfNeeded(
                auction, LocalDateTime.now());

        assertFalse(extended2, "Lần 2 không được extend");
        assertEquals(afterFirst, auction.getEndTime());
    }

    // ══════════════════════════════════════════════════════════════
    // TEST 4 — Auto-bid cơ bản: 1 bidder outbid manual bid
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Auto-bid: 1 bidder tự động outbid manual bid")
    void autoBid_shouldOutbidManualBid() {
        Auction auction = TestDataFactory.runningAuction(5_000_000L, 100_000L, 300);

        // Lưu config TRƯỚC khi bid để autoBidDao có data
        AutoBidConfig config = TestDataFactory.autoBidConfig(
                auction.getId(), "auto-bidder", 8_000_000L, 100_000L);
        autoBidDao.save(config);

        // Manual bid của đối thủ
        auction.addBid(TestDataFactory.bid(
                auction.getId(), "manual-bidder", 5_200_000L));

        boolean result = autoBidService.resolveAutoBids(auction);

        assertTrue(result, "Auto-bid phải phản ứng");
        assertEquals("auto-bidder", auction.getHighestBidderId(),
                "auto-bidder phải dẫn đầu sau resolve");
        assertTrue(auction.getCurrentPrice() > 5_200_000L,
                "Giá phải cao hơn manual bid");
    }

    // ══════════════════════════════════════════════════════════════
    // TEST 5 — Auto-bid không bid khi đang dẫn đầu
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Auto-bid không tự bid khi mình đang dẫn đầu")
    void autoBid_shouldNotBidWhenLeading() {
        Auction auction = TestDataFactory.runningAuction(5_000_000L, 100_000L, 300);

        // auto-bidder đang dẫn đầu
        auction.addBid(TestDataFactory.bid(
                auction.getId(), "auto-bidder", 5_200_000L));

        autoBidDao.save(TestDataFactory.autoBidConfig(
                auction.getId(), "auto-bidder", 10_000_000L, 100_000L));

        boolean result = autoBidService.resolveAutoBids(auction);

        assertFalse(result, "Không được auto-bid khi mình đang dẫn đầu");
        assertEquals(1, auction.getBidHistory().size());
        assertEquals("auto-bidder", auction.getHighestBidderId());
    }

    // ══════════════════════════════════════════════════════════════
    // TEST 6 — ScenarioB: 2 auto-bidder — winner đúng
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ScenarioB: auto-B (max cao hơn) thắng")
    void scenarioB_higherMaxAmountWins() {
        AdvancedFeatureScenarioFactory.ScenarioB scenario =
                AdvancedFeatureScenarioFactory.createScenarioB();

        Auction auction = scenario.auction;
        autoBidDao.save(scenario.configA);
        autoBidDao.save(scenario.configB);

        auction.addBid(TestDataFactory.bid(
                auction.getId(), scenario.manualBidderId, scenario.manualBidAmount));

        autoBidService.resolveAutoBids(auction);

        assertEquals(scenario.expectedWinner, auction.getHighestBidderId(),
                "Bidder maxAmount cao hơn phải thắng");

        // Giá phải tăng dần
        List<Long> prices = AdvancedFeatureScenarioFactory
                .extractPrices(auction.getBidHistory());
        for (int i = 1; i < prices.size(); i++) {
            assertTrue(prices.get(i) > prices.get(i - 1),
                    "Giá phải tăng dần tại index " + i);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // InMemoryAutoBidDao
    // ══════════════════════════════════════════════════════════════

    static class InMemoryAutoBidDao implements AutoBidDao {

        private final List<AutoBidConfig> configs = new ArrayList<>();

        @Override
        public List<AutoBidConfig> findByAuctionId(String auctionId) {
            return configs.stream()
                    .filter(c -> c.getAuctionId().equals(auctionId))
                    .collect(Collectors.toList());
        }

        @Override
        public Optional<AutoBidConfig> findByAuctionIdAndBidderId(String auctionId,
                                                                    String bidderId) {
            return configs.stream()
                    .filter(c -> c.getAuctionId().equals(auctionId)
                            && c.getBidderId().equals(bidderId))
                    .findFirst();
        }

        @Override
        public void save(AutoBidConfig config) {
            configs.removeIf(c -> c.getAuctionId().equals(config.getAuctionId())
                    && c.getBidderId().equals(config.getBidderId()));
            configs.add(config);
        }

        @Override
        public void deleteById(String configId) {
            configs.removeIf(c -> c.getId().equals(configId));
        }

        @Override
        public List<AutoBidConfig> findAll() {
            return new ArrayList<>(configs);
        }
    }
}