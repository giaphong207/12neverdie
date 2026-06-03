package com.auction.server.service;

import com.auction.server.concurrency.AuctionLockManager;
import com.auction.server.dao.AutoBidDao;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.bid.AutoBidConfig;
import com.auction.shared.model.bid.Bid;
import com.auction.shared.model.bid.BidSource;
import com.auction.support.AdvancedFeatureScenarioFactory;
import com.auction.support.AdvancedFeatureScenarioFactory.ScenarioB;
import com.auction.support.AdvancedFeatureScenarioFactory.ScenarioC;
import com.auction.support.TestDataFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AutoBidService - lưu config + cascade resolveAutoBids")
class AutoBidServiceTest {

    private FakeAutoBidDao autoBidDao;
    private AuctionLockManager lockManager;
    private DefaultAutoBidService service;
    private static final java.util.function.ToLongFunction<String> UNLIMITED = id -> Long.MAX_VALUE;

    @BeforeEach
    void setUp() {
        autoBidDao = new FakeAutoBidDao();
        lockManager = new AuctionLockManager();
        service = new DefaultAutoBidService(autoBidDao, lockManager);
    }

    @Nested
    @DisplayName("Phase 1: CRUD AutoBidConfig")
    class DaoCrud {

        @Test
        @DisplayName("Save autoBidConfig và lấy lại được")
        void save_and_retrieve_autobid_config() {
            String cfgId = UUID.randomUUID().toString();
            AutoBidConfig cfg = new AutoBidConfig(
                    cfgId, "auction-1", "bidder-1", 5000L, 200L);
            autoBidDao.save(cfg);

            Optional<AutoBidConfig> found = autoBidDao.findByAuctionIdAndBidderId(
                    "auction-1", "bidder-1");
            assertTrue(found.isPresent());
            assertEquals(5000L, found.get().getMaxAmount());
            assertEquals(200L, found.get().getIncrement());
        }

        @Test
        @DisplayName("findByAuctionId() lọc đúng config theo auction")
        void find_by_auction_id() {
            autoBidDao.save(new AutoBidConfig(UUID.randomUUID().toString(),
                    "auction-1", "bidder-1", 5000L, 100L));
            autoBidDao.save(new AutoBidConfig(UUID.randomUUID().toString(),
                    "auction-1", "bidder-2", 6000L, 100L));
            autoBidDao.save(new AutoBidConfig(UUID.randomUUID().toString(),
                    "auction-2", "bidder-3", 7000L, 100L));

            assertEquals(2, autoBidDao.findByAuctionId("auction-1").size());
            assertEquals(1, autoBidDao.findByAuctionId("auction-2").size());
        }

        @Test
        @DisplayName("findByAuctionIdAndBidderId() trả empty nếu không tồn tại")
        void find_not_exist_returns_empty() {
            Optional<AutoBidConfig> result = autoBidDao.findByAuctionIdAndBidderId(
                    "ghost-auction", "ghost-bidder");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("findAll() trả về toàn bộ config")
        void find_all() {
            assertEquals(0, autoBidDao.findAll().size());
            autoBidDao.save(new AutoBidConfig(UUID.randomUUID().toString(),
                    "a-1", "b-1", 5000L, 100L));
            autoBidDao.save(new AutoBidConfig(UUID.randomUUID().toString(),
                    "a-2", "b-2", 6000L, 100L));
            assertEquals(2, autoBidDao.findAll().size());
        }

        @Test
        @DisplayName("deleteById() xóa config khỏi DAO")
        void delete_by_id() {
            String cfgId = UUID.randomUUID().toString();
            autoBidDao.save(new AutoBidConfig(cfgId, "a-1", "b-1", 5000L, 100L));
            assertEquals(1, autoBidDao.findAll().size());

            autoBidDao.deleteById(cfgId);
            assertEquals(0, autoBidDao.findAll().size());
        }
    }

    @Nested
    @DisplayName("Phase 2: upsertConfig + disableConfig qua service")
    class ServiceCrud {

        @Test
        @DisplayName("upsertConfig: tạo mới khi chưa có")
        void upsert_creates_new() {
            service.upsertConfig("a-1", "b-1", 10_000L, 500L);

            Optional<AutoBidConfig> found = autoBidDao.findByAuctionIdAndBidderId("a-1", "b-1");
            assertTrue(found.isPresent());
            assertEquals(10_000L, found.get().getMaxAmount());
            assertEquals(500L, found.get().getIncrement());
            assertTrue(found.get().isEnabled());
        }

        @Test
        @DisplayName("upsertConfig: cập nhật + enable lại nếu đã có")
        void upsert_updates_existing() {
            service.upsertConfig("a-1", "b-1", 10_000L, 500L);
            service.disableConfig("a-1", "b-1");

            service.upsertConfig("a-1", "b-1", 20_000L, 1000L);

            Optional<AutoBidConfig> found = autoBidDao.findByAuctionIdAndBidderId("a-1", "b-1");
            assertTrue(found.isPresent());
            assertEquals(20_000L, found.get().getMaxAmount());
            assertEquals(1000L, found.get().getIncrement());
            assertTrue(found.get().isEnabled());
        }

        @Test
        @DisplayName("disableConfig: tắt config đang có")
        void disable_existing_config() {
            service.upsertConfig("a-1", "b-1", 10_000L, 500L);

            boolean disabled = service.disableConfig("a-1", "b-1");

            assertTrue(disabled);
            assertFalse(autoBidDao.findByAuctionIdAndBidderId("a-1", "b-1").get().isEnabled());
        }

        @Test
        @DisplayName("disableConfig: không có config → trả false")
        void disable_non_existing_returns_false() {
            assertFalse(service.disableConfig("ghost-auction", "ghost-bidder"));
        }
    }

    @Nested
    @DisplayName("Phase 3: resolveAutoBids cascade")
    class Cascade {

        @Test
        @DisplayName("Không có config nào → trả false, không tạo bid")
        void no_config_returns_false() {
            Auction auction = TestDataFactory.runningAuction(5_000_000L, 100_000L, 300);
            int sizeBefore = auction.getBidHistory().size();

            boolean result = service.resolveAutoBids(auction,UNLIMITED);

            assertFalse(result);
            assertEquals(sizeBefore, auction.getBidHistory().size());
        }

        @Test
        @DisplayName("Config bị disabled → không cascade")
        void disabled_config_does_not_cascade() {
            Auction auction = TestDataFactory.runningAuction(5_000_000L, 100_000L, 300);
            AutoBidConfig cfg = TestDataFactory.autoBidConfig(
                    auction.getId(), "bidder-A", 7_000_000L, 100_000L);
            cfg.disable();
            autoBidDao.save(cfg);

            boolean result = service.resolveAutoBids(auction,UNLIMITED);

            assertFalse(result);
            assertEquals(0, auction.getBidHistory().size());
        }

        @Test
        @DisplayName("1 auto-bidder: tạo 1 bid AUTO vừa đủ vượt minIncrement")
        void single_autobidder_places_one_bid() {
            Auction auction = TestDataFactory.runningAuction(5_000_000L, 100_000L, 300);
            autoBidDao.save(TestDataFactory.autoBidConfig(
                    auction.getId(), "bidder-A", 7_000_000L, 100_000L));

            boolean result = service.resolveAutoBids(auction,UNLIMITED);

            assertTrue(result);
            assertEquals(1, auction.getBidHistory().size());
            Bid b = auction.getBidHistory().get(0);
            assertEquals(BidSource.AUTO, b.getSource());
            assertEquals("bidder-A", b.getBidderId());
            assertEquals(5_100_000L, b.getAmount());
        }

        @Test
        @DisplayName("2 auto-bidder: người max cao thắng cuộc cascade")
        void two_autobidders_higher_max_wins() {
            ScenarioB s = AdvancedFeatureScenarioFactory.createScenarioB();

            Bid manualBid = TestDataFactory.bid(
                    s.auction.getId(), s.manualBidderId, s.manualBidAmount);
            s.auction.addBid(manualBid);

            autoBidDao.save(s.configA);
            autoBidDao.save(s.configB);

            boolean result = service.resolveAutoBids(s.auction,UNLIMITED);

            assertTrue(result);
            assertEquals(s.expectedWinner, s.auction.getHighestBidderId());
            assertEquals(s.expectedFinalPrice, s.auction.getCurrentPrice());
            assertTrue(AdvancedFeatureScenarioFactory.countAutoBids(
                    s.auction.getBidHistory()) >= 1);
        }

        @Test
        @DisplayName("Tie-break: 2 auto-bidder cùng max → config tạo sớm thắng")
        void tie_break_by_created_at() {
            ScenarioC s = AdvancedFeatureScenarioFactory.createScenarioC();

            s.auction.addBid(TestDataFactory.bid(
                    s.auction.getId(), "external-bidder", 5_100_000L));

            autoBidDao.save(s.configEarly);
            autoBidDao.save(s.configLate);

            boolean result = service.resolveAutoBids(s.auction,UNLIMITED);

            assertTrue(result);
            assertEquals(s.expectedWinner, s.auction.getHighestBidderId());
        }

        @Test
        @DisplayName("Cascade dừng đúng lúc khi không ai còn outbid được")
        void cascade_terminates_correctly() {
            Auction auction = TestDataFactory.runningAuction(5_000_000L, 100_000L, 300);

            autoBidDao.save(TestDataFactory.autoBidConfig(
                    auction.getId(), "bidder-low", 5_300_000L, 100_000L));
            autoBidDao.save(TestDataFactory.autoBidConfig(
                    auction.getId(), "bidder-mid", 5_700_000L, 100_000L));
            autoBidDao.save(TestDataFactory.autoBidConfig(
                    auction.getId(), "bidder-high", 6_500_000L, 100_000L));

            boolean result = service.resolveAutoBids(auction,UNLIMITED);

            assertTrue(result);
            assertEquals("bidder-high", auction.getHighestBidderId());
            assertEquals(5_800_000L, auction.getCurrentPrice());
        }

        @Test
        @DisplayName("Leader hiện tại không được tự cascade ngược lại chính mình")
        void leader_does_not_cascade_against_self() {
            Auction auction = TestDataFactory.runningAuction(5_000_000L, 100_000L, 300);
            auction.addBid(TestDataFactory.bid(
                    auction.getId(), "bidder-A", 5_100_000L));

            autoBidDao.save(TestDataFactory.autoBidConfig(
                    auction.getId(), "bidder-A", 7_000_000L, 100_000L));

            int sizeBefore = auction.getBidHistory().size();
            service.resolveAutoBids(auction,UNLIMITED);

            assertEquals(sizeBefore, auction.getBidHistory().size());
            assertEquals("bidder-A", auction.getHighestBidderId());
        }

        @Test
        @DisplayName("Config có max chưa vượt nổi giá hiện tại → bỏ qua")
        void config_below_current_price_skipped() {
            Auction auction = TestDataFactory.runningAuction(5_000_000L, 100_000L, 300);
            autoBidDao.save(TestDataFactory.autoBidConfig(
                    auction.getId(), "bidder-too-low", 5_000_000L, 100_000L));

            boolean result = service.resolveAutoBids(auction,UNLIMITED);

            assertFalse(result);
            assertEquals(0, auction.getBidHistory().size());
        }

        @Test
        @DisplayName("Cascade tạo bid AUTO source — không phải MANUAL")
        void cascade_bids_are_auto_source() {
            Auction auction = TestDataFactory.runningAuction(5_000_000L, 100_000L, 300);
            autoBidDao.save(TestDataFactory.autoBidConfig(
                    auction.getId(), "bidder-A", 7_000_000L, 100_000L));

            service.resolveAutoBids(auction,UNLIMITED);

            assertEquals(BidSource.AUTO, auction.getBidHistory().get(0).getSource());
        }
        @Test
        @DisplayName("#1 trần hiệu dụng: trần khai cao nhưng ví thấp → bị giới hạn theo ví")
        void effective_ceiling_limits_by_balance() {
            Auction auction = TestDataFactory.runningAuction(5_000_000L, 100_000L, 300);
            auction.addBid(TestDataFactory.bid(auction.getId(), "trigger", 5_100_000L));

            autoBidDao.save(TestDataFactory.autoBidConfig(auction.getId(), "An",   6_000_000L, 100_000L));
            autoBidDao.save(TestDataFactory.autoBidConfig(auction.getId(), "Binh", 9_000_000L, 100_000L));

            Map<String, Long> balance = Map.of("An", 6_000_000L, "Binh", 5_500_000L);

            boolean result = service.resolveAutoBids(auction, id -> balance.getOrDefault(id, 0L));

            assertTrue(result);
            // Binh trần khai 9M nhưng ví 5.5M → eff 5.5M < An 6M → An thắng
            assertEquals("An", auction.getHighestBidderId());
            assertEquals(5_600_000L, auction.getCurrentPrice());   // runnerUp eff 5.5M + 1 bước
        }
    }

    // ===== FAKE DAO =====
    static class FakeAutoBidDao implements AutoBidDao {
        private final Map<String, AutoBidConfig> configs = new HashMap<>();

        @Override public void save(AutoBidConfig cfg) { configs.put(cfg.getId(), cfg); }

        @Override
        public Optional<AutoBidConfig> findByAuctionIdAndBidderId(String auctionId, String bidderId) {
            return configs.values().stream()
                    .filter(c -> c.getAuctionId().equals(auctionId)
                            && c.getBidderId().equals(bidderId))
                    .findFirst();
        }

        @Override
        public List<AutoBidConfig> findByAuctionId(String auctionId) {
            return configs.values().stream()
                    .filter(c -> c.getAuctionId().equals(auctionId))
                    .collect(Collectors.toList());
        }

        @Override public void deleteById(String configId) { configs.remove(configId); }

        @Override public List<AutoBidConfig> findAll() { return new ArrayList<>(configs.values()); }
    }
}
