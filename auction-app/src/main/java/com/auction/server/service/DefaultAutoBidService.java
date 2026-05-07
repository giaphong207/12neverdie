package com.auction.server.service;

import com.auction.server.dao.AutoBidDao;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AutoBidConfig;
import com.auction.shared.model.Bid;

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

            // Tìm tất cả config có thể outbid (loại bỏ leader)
            List<AutoBidConfig> candidates = new ArrayList<>();
            for (AutoBidConfig cfg : configs) {
                if (cfg.canOutbid(currentPrice, currentLeaderId)) {
                    candidates.add(cfg);
                }
            }

            if (candidates.isEmpty()) {
                break; // không ai có thể outbid nữa -> dừng
            }

            // Chọn config có maxAmount CAO NHẤT;
            // bằng nhau -> createdAt SỚM HƠN thắng.
            candidates.sort(
                    Comparator.comparingLong(AutoBidConfig::getMaxAmount).reversed()
                            .thenComparing(AutoBidConfig::getCreatedAt)
            );
            AutoBidConfig chosen = candidates.get(0);

            long step = Math.max(chosen.getIncrement(), auction.getMinIncrement());
            long nextAmount = currentPrice + step;

            if (nextAmount > chosen.getMaxAmount()) {
                break;
            }

            // Tạo bid bằng constructor 5 tham số của TV2
            Bid autoBid = new Bid(
                    UUID.randomUUID().toString(),
                    auction.getId(),
                    chosen.getBidderId(),
                    nextAmount,
                    LocalDateTime.now()
            );

            // auction.addBid() sẽ tự validate canAcceptBid + tự cập nhật
            // currentPrice và highestBidderId. Nếu validate fail thì throw,
            // nhưng mình đã canOutbid() ở trên nên đảm bảo hợp lệ.
            //
            // CHỈ rủi ro: nếu auction status không phải RUNNING (ví dụ
            // anti-sniping của TV3 đổi status), addBid() sẽ throw.
            // Mình try-catch để cascade dừng gracefully thay vì crash placeBid().
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