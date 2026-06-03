package com.auction.server.service;

import com.auction.server.dao.AutoBidDao;
import com.auction.server.concurrency.AuctionLockManager;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.bid.AutoBidConfig;
import com.auction.shared.model.bid.Bid;
import com.auction.shared.model.bid.BidSource;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToLongFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultAutoBidService implements AutoBidService {
    private static final Logger log = LoggerFactory.getLogger(DefaultAutoBidService.class);

    private static final int MAX_CASCADE_ITERATIONS = 1000;
    private static final long LOCK_TIMEOUT_SECONDS = 2L;

    private final AutoBidDao autoBidDao;
    private final AuctionLockManager lockManager;
    public DefaultAutoBidService(AutoBidDao autoBidDao,
                                 AuctionLockManager lockManager) {
        this.autoBidDao = autoBidDao;
        this.lockManager = lockManager;
    }

    // ==================== CRUD ====================

    @Override
    public void upsertConfig(String auctionId, String bidderId,
                             long maxAmount, long increment) {
        ReentrantLock lock = lockManager.getLock(auctionId);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bị gián đoạn khi chờ lock cho auction " + auctionId);
        }
        if (!acquired) {
            throw new IllegalStateException("Hệ thống đang bận, vui lòng thử lại");
        }

        try {
            // === logic cũ giữ nguyên ===
            Optional<AutoBidConfig> existing =
                    autoBidDao.findByAuctionIdAndBidderId(auctionId, bidderId);

            if (existing.isPresent()) {
                AutoBidConfig cfg = existing.get();
                cfg.updateMaxAmount(maxAmount);
                cfg.updateIncrement(increment);
                cfg.enable();
                autoBidDao.save(cfg);
            } else {
                AutoBidConfig cfg = new AutoBidConfig(
                        UUID.randomUUID().toString(),
                        auctionId, bidderId, maxAmount, increment);
                autoBidDao.save(cfg);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<AutoBidConfig> getConfigsByAuction(String auctionId) {
        return autoBidDao.findByAuctionId(auctionId);
    }

    @Override
    public boolean disableConfig(String auctionId, String bidderId) {
        ReentrantLock lock = lockManager.getLock(auctionId);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bị gián đoạn khi chờ lock cho auction " + auctionId);
        }
        if (!acquired) {
            throw new IllegalStateException("Hệ thống đang bận, vui lòng thử lại");
        }

        try {
            Optional<AutoBidConfig> existing =
                    autoBidDao.findByAuctionIdAndBidderId(auctionId, bidderId);
            if (existing.isEmpty()) return false;
            AutoBidConfig cfg = existing.get();
            cfg.disable();
            autoBidDao.save(cfg);
            return true;
        } finally {
            lock.unlock();
        }
    }

    // ==================== Cascade resolve ====================

    @Override
    public boolean resolveAutoBids(Auction auction, ToLongFunction<String> balanceOf) {
        List<AutoBidConfig> configs = autoBidDao.findByAuctionId(auction.getId());
        if (configs.isEmpty()) return false;

        // Fix #1: trần hiệu dụng = min(maxAmount, số dư). Đọc số dư MỘT lần cho mỗi
        // config — cascade chỉ ĐẶT bid (không TRỪ tiền) nên số dư là hằng số trong
        // suốt một lần resolve.
        Map<String, Long> ceilingByConfigId = new HashMap<>();
        for (AutoBidConfig c : configs) {
            long balance = balanceOf.applyAsLong(c.getBidderId());
            ceilingByConfigId.put(c.getId(), c.effectiveCeiling(balance));
        }

        boolean anyAutoBidPlaced = false;
        int iterations = 0;

        while (iterations++ < MAX_CASCADE_ITERATIONS) {
            long currentPrice = auction.getCurrentPrice();
            String currentLeaderId = auction.getHighestBidderId();
            long minIncrement = auction.getMinIncrement();

            // Filter + sort — đều theo TRẦN HIỆU DỤNG, không phải maxAmount thô.
            List<AutoBidConfig> candidates = configs.stream()
                    .filter(c -> c.canOutbid(currentPrice, currentLeaderId, minIncrement,
                            ceilingByConfigId.get(c.getId())))
                    .sorted(Comparator.comparingLong(
                                    (AutoBidConfig c) -> ceilingByConfigId.get(c.getId()))
                            .reversed()
                            .thenComparing(AutoBidConfig::getCreatedAt))
                    .toList();

            if (candidates.isEmpty()) break;

            AutoBidConfig chosen = candidates.get(0);
            long runnerUpCeiling = candidates.size() > 1
                    ? ceilingByConfigId.get(candidates.get(1).getId())
                    : currentPrice;

            long nextAmount = chosen.calculateNextAmount(
                    currentPrice, runnerUpCeiling, minIncrement,
                    ceilingByConfigId.get(chosen.getId()));

            auction.addBid(Bid.createNew(
                    auction.getId(), chosen.getBidderId(), nextAmount, BidSource.AUTO));
            anyAutoBidPlaced = true;
        }

        if (iterations >= MAX_CASCADE_ITERATIONS) {
            log.warn("Cascade reached MAX_CASCADE_ITERATIONS for auction {}", auction.getId());
        }
        return anyAutoBidPlaced;
    }
}