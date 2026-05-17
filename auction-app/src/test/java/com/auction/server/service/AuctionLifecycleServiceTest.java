package com.auction.server.service;

import com.auction.server.dao.AuctionDao;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuctionLifecycleService - chuyển trạng thái theo thời gian")
class AuctionLifecycleServiceTest {

    private FakeAuctionDao auctionDao;
    private DefaultAuctionLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        auctionDao = new FakeAuctionDao();
        lifecycleService = new DefaultAuctionLifecycleService(auctionDao);
    }

    @Test
    @DisplayName("OPEN trong tương lai → vẫn OPEN sau update")
    void open_in_future_stays_open() {
        LocalDateTime now = LocalDateTime.now();
        String auctionId = UUID.randomUUID().toString();
        Auction auction = new Auction(auctionId, "item", "seller",
                100L, 10L, AuctionStatus.OPEN,
                now.plusMinutes(30), now.plusHours(1));
        auctionDao.save(auction);

        lifecycleService.updateStatusByTime(auctionId);

        Auction reloaded = auctionDao.findById(auctionId).orElseThrow();
        assertEquals(AuctionStatus.OPEN, reloaded.getStatus());
    }

    @Test
    @DisplayName("OPEN đến giờ → chuyển RUNNING")
    void open_reached_start_time_becomes_running() {
        LocalDateTime now = LocalDateTime.now();
        String auctionId = UUID.randomUUID().toString();
        Auction auction = new Auction(auctionId, "item", "seller",
                100L, 10L, AuctionStatus.OPEN,
                now.minusMinutes(1), now.plusHours(1));
        auctionDao.save(auction);

        lifecycleService.updateStatusByTime(auctionId);

        Auction reloaded = auctionDao.findById(auctionId).orElseThrow();
        assertEquals(AuctionStatus.RUNNING, reloaded.getStatus());
    }

    @Test
    @DisplayName("RUNNING hết hạn → chuyển FINISHED")
    void running_expired_becomes_finished() {
        LocalDateTime now = LocalDateTime.now();
        String auctionId = UUID.randomUUID().toString();
        Auction auction = new Auction(auctionId, "item", "seller",
                100L, 10L, AuctionStatus.RUNNING,
                now.minusHours(2), now.minusMinutes(1));
        auctionDao.save(auction);

        lifecycleService.updateStatusByTime(auctionId);

        Auction reloaded = auctionDao.findById(auctionId).orElseThrow();
        assertEquals(AuctionStatus.FINISHED, reloaded.getStatus());
    }

    @Test
    @DisplayName("RUNNING chưa hết hạn → giữ RUNNING")
    void running_not_expired_stays_running() {
        LocalDateTime now = LocalDateTime.now();
        String auctionId = UUID.randomUUID().toString();
        Auction auction = new Auction(auctionId, "item", "seller",
                100L, 10L, AuctionStatus.RUNNING,
                now.minusMinutes(5), now.plusMinutes(30));
        auctionDao.save(auction);

        lifecycleService.updateStatusByTime(auctionId);

        Auction reloaded = auctionDao.findById(auctionId).orElseThrow();
        assertEquals(AuctionStatus.RUNNING, reloaded.getStatus());
    }

    @Test
    @DisplayName("FINISHED không bị đổi lại")
    void finished_stays_finished() {
        LocalDateTime now = LocalDateTime.now();
        String auctionId = UUID.randomUUID().toString();
        Auction auction = new Auction(auctionId, "item", "seller",
                100L, 10L, AuctionStatus.FINISHED,
                now.minusHours(2), now.minusHours(1));
        auctionDao.save(auction);

        lifecycleService.updateStatusByTime(auctionId);

        Auction reloaded = auctionDao.findById(auctionId).orElseThrow();
        assertEquals(AuctionStatus.FINISHED, reloaded.getStatus());
    }

    // ===== FAKE DAO =====
    static class FakeAuctionDao implements AuctionDao {
        private final Map<String, Auction> auctions = new HashMap<>();

        @Override
        public void save(Auction auction) {
            auctions.put(auction.getId(), auction);
        }

        @Override
        public Optional<Auction> findById(String id) {
            return id == null ? Optional.empty() : Optional.ofNullable(auctions.get(id));
        }

        @Override
        public List<Auction> findAll() {
            return new ArrayList<>(auctions.values());
        }

        @Override
        public List<Auction> findActiveAuctions() {
            return auctions.values().stream()
                    .filter(a -> a.getStatus() == AuctionStatus.OPEN
                            || a.getStatus() == AuctionStatus.RUNNING)
                    .collect(Collectors.toList());
        }

        @Override
        public void update(Connection conn, Auction auction) {
            auctions.put(auction.getId(), auction);
        }

        @Override
        public void deleteById(String id) {
            auctions.remove(id);
        }
    }
}