package com.auction.server.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.auction.server.dao.AutoBidDao;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AutoBidConfig;
import com.auction.shared.model.Bid;
import com.auction.shared.model.BidSource;
import com.auction.support.AdvancedFeatureScenarioFactory;
import com.auction.support.TestDataFactory;

/**
 * Integration test cho chuỗi xử lý đầy đủ tuần 5.
 *
 * TV1 sở hữu. Test chuỗi:
 *   manual bid → anti-sniping → auto-bid cascade → kiểm tra final state.
 *
 * Không dùng socket thật, không dùng file.
 * Dùng InMemoryAutoBidDao để test độc lập.
 *
 * LƯU Ý: Test này cần TV3 và TV4 đã implement:
 *   - DefaultAntiSnipingService
 *   - DefaultAutoBidService
 * Nếu chưa có, test sẽ fail compile — bình thường, chờ TV3/TV4 merge xong.
 */
class AdvancedFeatureIntegrationTest {

    // TV3 implement — import đúng package khi TV3 đã merge
    private AntiSnipingService antiSnipingService;

    // TV4 implement — import đúng package khi TV4 đã merge
    private AutoBidService autoBidService;

    private InMemoryAutoBidDao autoBidDao;

    @BeforeEach
    void setUp() {
        autoBidDao = new InMemoryAutoBidDao();

        // Thay tên class đúng với implementation của TV3/TV4
        // antiSnipingService = new DefaultAntiSnipingService();
        // autoBidService     = new DefaultAutoBidService(autoBidDao);

        // Tạm thời dùng anonymous class để test compile được ngay
        // Xóa 2 block này khi TV3/TV4 đã merge implementation thật
        antiSnipingService = (auction, triggerTime) -> {
            throw new UnsupportedOperationException(
                    "Chờ TV3 merge DefaultAntiSnipingService");
        };
        autoBidService = new AutoBidService() {
            @Override public void upsertConfig(String auctionId, String bidderId,
                                               long maxAmount, long increment) {
                throw new UnsupportedOperationException("Chờ TV4");
            }
            @Override public List<AutoBidConfig> getConfigsByAuction(String auctionId) {
                throw new UnsupportedOperationException("Chờ TV4");
            }
            @Override public boolean disableConfig(String auctionId, String bidderId) {
                throw new UnsupportedOperationException("Chờ TV4");
            }
            @Override public boolean resolveAutoBids(Auction auction) {
                throw new UnsupportedOperationException("Chờ TV4");
            }
        };
    }

    // ══════════════════════════════════════════════════════════════
    // TEST 1 — ScenarioA: manual bid cuối phiên → anti-snipe → auto-bid
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ScenarioA: manual bid ở 20s cuối → anti-sniping extend → auto-bid phản ứng")
    void scenarioA_fullFlow() {
        AdvancedFeatureScenarioFactory.ScenarioA scenario =
                AdvancedFeatureScenarioFactory.createScenarioA();

        Auction auction = scenario.auction;
        autoBidDao.save(scenario.autoBidConfig);
        LocalDateTime originalEndTime = auction.getEndTime();

        // Step 1: manual bid
        LocalDateTime bidTime = LocalDateTime.now();
        Bid manualBid = TestDataFactory.bid(
                auction.getId(), scenario.manualBidderId, 6_100_000L);
        auction.addBid(manualBid);

        // Step 2: anti-sniping
        boolean extended = antiSnipingService.applyExtensionIfNeeded(auction, bidTime);

        assertTrue(extended, "Anti-sniping phải extend khi còn <= 30 giây");
        assertTrue(auction.getEndTime().isAfter(originalEndTime),
                "endTime phải tăng sau anti-sniping");
        long extensionSeconds = java.time.temporal.ChronoUnit.SECONDS
                .between(originalEndTime, auction.getEndTime());
        assertEquals(60L, extensionSeconds, "Extension phải đúng 60 giây");

        // Step 3: auto-bid
        boolean autoBidded = autoBidService.resolveAutoBids(auction);

        assertTrue(autoBidded, "Auto-bid phải phản ứng sau manual bid");
        assertTrue(auction.getCurrentPrice() > 6_100_000L,
                "Giá cuối phải cao hơn manual bid");
        assertEquals(scenario.autoBidderId, auction.getHighestBidderId(),
                "Bidder có auto-bid config phải dẫn đầu");

        long autoBidCount = AdvancedFeatureScenarioFactory
                .countAutoBids(auction.getBidHistory());
        assertTrue(autoBidCount >= 1, "Phải có ít nhất 1 auto-bid trong bidHistory");
        assertNotEquals(originalEndTime, auction.getEndTime());
    }

    // ══════════════════════════════════════════════════════════════
    // TEST 2 — ScenarioB: 2 auto-bidder — final price đúng
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ScenarioB: auto-B (max cao hơn) thắng, giá cuối đúng 5_600_000")
    void scenarioB_twoAutoBiddersFinalPrice() {
        AdvancedFeatureScenarioFactory.ScenarioB scenario =
                AdvancedFeatureScenarioFactory.createScenarioB();

        Auction auction = scenario.auction;
        autoBidDao.save(scenario.configA);
        autoBidDao.save(scenario.configB);

        // Manual bid trigger
        Bid manualBid = TestDataFactory.bid(
                auction.getId(),
                scenario.manualBidderId,
                scenario.manualBidAmount);
        auction.addBid(manualBid);

        autoBidService.resolveAutoBids(auction);

        assertEquals(scenario.expectedWinner, auction.getHighestBidderId(),
                "Bidder maxAmount cao hơn phải thắng");
        assertEquals(scenario.expectedFinalPrice, auction.getCurrentPrice(),
                "Giá cuối phải đúng: 1 step sau max của auto-A");

        // Giá phải tăng dần trong history
        List<Long> prices = AdvancedFeatureScenarioFactory
                .extractPrices(auction.getBidHistory());
        for (int i = 1; i < prices.size(); i++) {
            assertTrue(prices.get(i) > prices.get(i - 1),
                    "Giá phải tăng dần tại index " + i);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // TEST 3 — ScenarioC: tie-break theo createdAt
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ScenarioC: 2 config cùng maxAmount — config tạo sớm hơn thắng")
    void scenarioC_tieBreakByCreatedAt() {
        AdvancedFeatureScenarioFactory.ScenarioC scenario =
                AdvancedFeatureScenarioFactory.createScenarioC();

        Auction auction = scenario.auction;
        autoBidDao.save(scenario.configEarly);
        autoBidDao.save(scenario.configLate);

        Bid manualBid = TestDataFactory.bid(
                auction.getId(), "manual-bidder", 5_100_000L);
        auction.addBid(manualBid);

        autoBidService.resolveAutoBids(auction);

        assertEquals(scenario.expectedWinner, auction.getHighestBidderId(),
                "Config tạo sớm hơn phải thắng khi maxAmount bằng nhau");
    }

    // ══════════════════════════════════════════════════════════════
    // TEST 4 — Anti-sniping chỉ extend 1 lần
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Anti-sniping chỉ extend 1 lần — lần 2 không extend vì đã đủ thời gian")
    void antiSnipingShouldOnlyExtendOnce() {
        Auction auction = TestDataFactory.auctionAboutToEnd();
        LocalDateTime originalEnd = auction.getEndTime();

        // Lần 1: manual bid → extend
        boolean extended1 = antiSnipingService.applyExtensionIfNeeded(
                auction, LocalDateTime.now());
        LocalDateTime afterFirstExtend = auction.getEndTime();

        assertTrue(extended1, "Lần 1 phải extend");
        assertNotEquals(originalEnd, afterFirstExtend);

        // Lần 2: sau extend auction còn ~80s → không extend nữa
        boolean extended2 = antiSnipingService.applyExtensionIfNeeded(
                auction, LocalDateTime.now());

        assertFalse(extended2, "Lần 2 không được extend vì đã đủ thời gian");
        assertEquals(afterFirstExtend, auction.getEndTime(),
                "endTime không đổi sau lần 2");
    }

    // ══════════════════════════════════════════════════════════════
    // TEST 5 — bidHistory đúng thứ tự: manual trước, auto sau
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("bidHistory chứa manual bid trước, auto-bid sau, giá tăng dần")
    void bidHistoryShouldContainManualThenAutoBids() {
        Auction auction = TestDataFactory.runningAuction(5_000_000L, 100_000L, 300);

        AutoBidConfig config = TestDataFactory.autoBidConfig(
                auction.getId(), "auto-bidder", 7_000_000L, 200_000L);
        autoBidDao.save(config);

        // Manual bid đầu tiên
        Bid manual = TestDataFactory.bid(
                auction.getId(), "manual-bidder", 5_200_000L);
        auction.addBid(manual);

        int beforeAuto = auction.getBidHistory().size();
        autoBidService.resolveAutoBids(auction);
        int afterAuto = auction.getBidHistory().size();

        assertTrue(afterAuto > beforeAuto, "bidHistory phải tăng sau auto-bid");

        // Bid đầu tiên phải là MANUAL
        assertEquals(BidSource.MANUAL,
                auction.getBidHistory().get(0).getSource(),
                "Bid đầu tiên phải là MANUAL");

        // Phải có AUTO bid
        long autoCount = AdvancedFeatureScenarioFactory
                .countAutoBids(auction.getBidHistory());
        assertTrue(autoCount >= 1, "Phải có ít nhất 1 auto-bid");

        // Giá phải tăng dần
        List<Long> prices = AdvancedFeatureScenarioFactory
                .extractPrices(auction.getBidHistory());
        for (int i = 1; i < prices.size(); i++) {
            assertTrue(prices.get(i) > prices.get(i - 1),
                    "Giá phải tăng dần");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // TEST 6 — Không tự bid khi đang dẫn đầu
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Auto-bid không tự bid khi mình đang là highest bidder")
    void autoBidShouldNotBidWhenAlreadyLeading() {
        Auction auction = TestDataFactory.runningAuction(5_000_000L, 100_000L, 300);

        // auto-bidder tự đặt manual bid → đang dẫn đầu
        Bid ownBid = TestDataFactory.bid(
                auction.getId(), "auto-bidder", 5_200_000L);
        auction.addBid(ownBid);

        // Config cho chính auto-bidder đang dẫn đầu
        AutoBidConfig config = TestDataFactory.autoBidConfig(
                auction.getId(), "auto-bidder", 10_000_000L, 100_000L);
        autoBidDao.save(config);

        boolean result = autoBidService.resolveAutoBids(auction);

        assertFalse(result, "Không được auto-bid khi mình đang dẫn đầu");
        assertEquals(1, auction.getBidHistory().size(),
                "bidHistory không tăng");
        assertEquals("auto-bidder", auction.getHighestBidderId());
    }

    // ══════════════════════════════════════════════════════════════
    // InMemoryAutoBidDao — chỉ dùng trong test này
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