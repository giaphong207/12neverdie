package com.auction.server.service;

import com.auction.server.DAO.AutoBidDao;
import com.auction.shared.model.AutoBidConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AutoBidService - lưu/load config autobid")
class AutoBidServiceTest {

    private FakeAutoBidDao autoBidDao;

    @BeforeEach
    void setUp() {
        autoBidDao = new FakeAutoBidDao();
    }

    @Test
    @DisplayName("Save autoBidConfig và lấy lại được")
    void save_and_retrieve_autobid_config() {
        String cfgId = UUID.randomUUID().toString();
        AutoBidConfig cfg = new AutoBidConfig(
                cfgId,
                "auction-1",
                "bidder-1",
                5000L, 200L);
        autoBidDao.save(cfg);

        Optional<AutoBidConfig> found = autoBidDao.findByAuctionIdAndBidderId("auction-1", "bidder-1");
        assertTrue(found.isPresent());
        assertEquals(5000L, found.get().getMaxAmount());
        assertEquals(200L, found.get().getIncrement());
    }

    @Test
    @DisplayName("findByAuctionId() — lấy tất cả config của 1 auction")
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
    @DisplayName("findByAuctionIdAndBidderId() — không tồn tại trả empty")
    void find_not_exist_returns_empty() {
        Optional<AutoBidConfig> result = autoBidDao.findByAuctionIdAndBidderId(
                "ghost-auction", "ghost-bidder");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findAll() — trả về tất cả config")
    void find_all() {
        assertEquals(0, autoBidDao.findAll().size());

        autoBidDao.save(new AutoBidConfig(UUID.randomUUID().toString(),
                "a-1", "b-1", 5000L, 100L));
        autoBidDao.save(new AutoBidConfig(UUID.randomUUID().toString(),
                "a-2", "b-2", 6000L, 100L));

        assertEquals(2, autoBidDao.findAll().size());
    }

    @Test
    @DisplayName("deleteById() — xóa config theo id")
    void delete_by_id() {
        String cfgId = UUID.randomUUID().toString();
        autoBidDao.save(new AutoBidConfig(cfgId, "a-1", "b-1", 5000L, 100L));
        assertEquals(1, autoBidDao.findAll().size());

        autoBidDao.deleteById(cfgId);
        assertEquals(0, autoBidDao.findAll().size());
    }

    // ===== FAKE DAO =====
    static class FakeAutoBidDao implements AutoBidDao {
        private final Map<String, AutoBidConfig> configs = new HashMap<>();

        @Override
        public void save(AutoBidConfig cfg) {
            configs.put(cfg.getId(), cfg);
        }

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

        @Override
        public void deleteById(String configId) {
            configs.remove(configId);
        }

        @Override
        public List<AutoBidConfig> findAll() {
            return new ArrayList<>(configs.values());
        }
    }
}