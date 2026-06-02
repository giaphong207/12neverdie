package com.auction.server.service;

import com.auction.server.concurrency.AuctionLockManager;
import com.auction.server.dao.AutoBidDao;
import com.auction.shared.model.bid.AutoBidConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AutoBidService - lưu/load + cascade")
class AutoBidServiceTest {

    private FakeAutoBidDao autoBidDao;
    private AuctionLockManager lockManager;
    private DefaultAutoBidService service;

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
