package com.auction.server.service;

import com.auction.server.DAO.AutoBidDao;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.bid.AutoBidConfig;
import com.auction.shared.model.bid.Bid;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation của AutoBidService.
 *
 * Thuật toán resolveAutoBids() (theo file phân công tuần 5):
 *
 *   while true:
 *     tìm tất cả AutoBidConfig của auction có thể outbid currentPrice
 *     bỏ config của current highest bidder
 *     nếu không còn ai -> dừng
 *
 *     chọn config có maxAmount cao nhất
 *     nếu bằng nhau -> chọn config tạo sớm hơn
 *
 *     nextAmount = currentPrice + chosen.increment
 *     nếu nextAmount > chosen.maxAmount -> dừng
 *
 *     tạo Bid và auction.addBid(autoBid)
 *
 * Safety: tối đa MAX_CASCADE_ITERATIONS lần lặp để tránh vòng lặp vô hạn.
 *
 * GHI CHÚ về Bid và Auction của TV2:
 *   - Bid là IMMUTABLE: phải tạo qua constructor có đủ 5 tham số.
 *   - Auction.addBid() đã tự validate qua canAcceptBid() và tự cập nhật
 *     currentPrice + highestBidderId. Mình không cần làm thêm.
 *   - Auction.getBidHistory() trả unmodifiableList -> KHÔNG được .add() vào,
 *     phải gọi auction.addBid() để lưu.
 */
public class DefaultAutoBidService implements AutoBidService {

    /** Trần số vòng lặp để tránh cascade chạy vô hạn vì lỗi config. */
    private static final int MAX_CASCADE_ITERATIONS = 1000;

    private final AutoBidDao autoBidDao;

    public DefaultAutoBidService(AutoBidDao autoBidDao) {
        this.autoBidDao = autoBidDao;
    }

    // ==================== CRUD ====================

    @Override
    public void upsertConfig(String auctionId, String bidderId,
                             long maxAmount, long increment) {
        // Constructor của AutoBidConfig đã validate maxAmount > 0 và increment > 0
        // nên không cần validate lại ở đây.

        Optional<AutoBidConfig> existing =
                autoBidDao.findByAuctionIdAndBidderId(auctionId, bidderId);

        if (existing.isPresent()) {
            // Cập nhật config cũ, GIỮ id và createdAt cũ để tie-break
            // "tạo sớm hơn" vẫn dùng được createdAt gốc.
            AutoBidConfig cfg = existing.get();
            cfg.updateMaxAmount(maxAmount);
            cfg.updateIncrement(increment);
            cfg.enable();
            autoBidDao.save(cfg);
        } else {
            AutoBidConfig cfg = new AutoBidConfig(
                    UUID.randomUUID().toString(),
                    auctionId,
                    bidderId,
                    maxAmount,
                    increment
            );
            autoBidDao.save(cfg);
        }
    }

    @Override
    public List<AutoBidConfig> getConfigsByAuction(String auctionId) {
        return autoBidDao.findByAuctionId(auctionId);
    }

    @Override
    public boolean disableConfig(String auctionId, String bidderId) {
        Optional<AutoBidConfig> existing =
                autoBidDao.findByAuctionIdAndBidderId(auctionId, bidderId);
        if (existing.isEmpty()) return false;
        AutoBidConfig cfg = existing.get();
        cfg.disable();
        autoBidDao.save(cfg);
        return true;
    }

    // ==================== Cascade resolve ====================

    @Override
    public boolean resolveAutoBids(Auction auction) {
        List<AutoBidConfig> configs = autoBidDao.findByAuctionId(auction.getId());
        if (configs.isEmpty()) {
            return false;
        }

        boolean anyAutoBidPlaced = false;
        int iterations = 0;

        while (iterations++ < MAX_CASCADE_ITERATIONS) {
            long currentPrice = auction.getCurrentPrice();
            String currentLeaderId = auction.getHighestBidderId();

            List<AutoBidConfig> candidates = new ArrayList<>();

            for (AutoBidConfig cfg : configs) {
                if (!cfg.isEnabled()) {
                    continue;
                }

                if (cfg.getBidderId().equals(currentLeaderId)) {
                    continue;
                }

                long step = Math.max(cfg.getIncrement(), auction.getMinIncrement());

                if (cfg.getMaxAmount() >= currentPrice + step) {
                    candidates.add(cfg);
                }
            }

            if (candidates.isEmpty()) {
                break;
            }

            candidates.sort(
                    Comparator.comparingLong(AutoBidConfig::getMaxAmount).reversed()
                            .thenComparing(AutoBidConfig::getCreatedAt)
            );

            AutoBidConfig chosen = candidates.get(0);

            long step = Math.max(chosen.getIncrement(), auction.getMinIncrement());

            long runnerUpMaxAmount = currentPrice;
            if (candidates.size() > 1) {
                runnerUpMaxAmount = candidates.get(1).getMaxAmount();
            }

            long nextAmount = Math.max(
                    currentPrice + step,
                    runnerUpMaxAmount + step
            );

            nextAmount = Math.min(nextAmount, chosen.getMaxAmount());

            Bid autoBid = new Bid(
                    UUID.randomUUID().toString(),
                    auction.getId(),
                    chosen.getBidderId(),
                    nextAmount,
                    LocalDateTime.now()
            );

            auction.addBid(autoBid);
            anyAutoBidPlaced = true;
        }

        if (iterations >= MAX_CASCADE_ITERATIONS) {
            System.err.println("[AutoBidService] WARNING: cascade reached MAX_CASCADE_ITERATIONS for auction "
                    + auction.getId());
        }

        return anyAutoBidPlaced;
    }
}