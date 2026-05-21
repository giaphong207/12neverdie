package com.auction.server.service;

import com.auction.server.concurrency.AuctionLockManager;
import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.BidDao;
import com.auction.server.dao.Database;
import com.auction.shared.exception.AuctionClosedException;
import com.auction.shared.exception.AuctionNotFoundException;
import com.auction.shared.exception.DataAccessException;
import com.auction.shared.exception.InvalidBidException;
import com.auction.shared.model.Auction;
import com.auction.shared.model.Bid;
import com.auction.shared.model.BidSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultBidService implements BidService {

    private final Database db;
    private final AuctionDao auctionDao;
    private final BidDao bidDao;
    private final AuctionLifecycleService lifecycleService;
    private final AuctionLockManager lockManager;
    private final AntiSnipingService antiSnipingService;
    private final AutoBidService autoBidService;

    public DefaultBidService(Database db,
                             AuctionDao auctionDao,
                             BidDao bidDao,
                             AuctionLifecycleService lifecycleService,
                             AuctionLockManager lockManager,
                             AntiSnipingService antiSnipingService,
                             AutoBidService autoBidService) {
        this.db = db;
        this.auctionDao = auctionDao;
        this.bidDao = bidDao;
        this.lifecycleService = lifecycleService;
        this.lockManager = lockManager;
        this.antiSnipingService = antiSnipingService;
        this.autoBidService = autoBidService;
    }

    @Override
    public BidResult placeBid(String auctionId, String bidderId, long amount) {
        // ① Validate input
        if (auctionId == null || auctionId.isBlank()) {
            throw new InvalidBidException("auctionId rỗng");
        }
        if (bidderId == null || bidderId.isBlank()) {
            throw new InvalidBidException("bidderId rỗng");
        }
        if (amount <= 0) {
            throw new InvalidBidException("Số tiền phải dương");
        }

        // ② Lấy lock per-auction
        ReentrantLock lock = lockManager.getLock(auctionId);
        boolean acquired;
        try {
            acquired = lock.tryLock(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InvalidBidException("Bị gián đoạn khi chờ lock");
        }
        if (!acquired) {
            throw new InvalidBidException("Hệ thống đang bận, vui lòng thử lại");
        }

        try {
            // ③ Update status theo thời gian
            lifecycleService.updateStatusByTime(auctionId);

            // ④ Load auction (kèm bid history)
            Auction auction = auctionDao.findById(auctionId)
                    .orElseThrow(() -> new AuctionNotFoundException(auctionId));

            // ⑤ Validate status
            if (!auction.isRunning()) {
                throw new AuctionClosedException(
                        "Phiên không hoạt động (trạng thái: " + auction.getStatus() + ")");
            }

            // ⑥ Validate amount
            if (!auction.canAcceptBid(amount)) {
                long required = auction.getCurrentPrice() + auction.getMinIncrement();
                throw new InvalidBidException(
                        "Số tiền phải >= " + required + " VNĐ");
            }

            // ⑦ Business rules
            if (auction.getSellerId().equals(bidderId)) {
                throw new InvalidBidException("Người bán không được đặt giá sản phẩm của mình");
            }
            if (bidderId.equals(auction.getHighestBidderId())) {
                throw new InvalidBidException("Bạn đang là người giữ giá cao nhất");
            }

            // ⑧ Tạo manual bid
            int sizeBefore = auction.getBidHistory().size();
            LocalDateTime now = LocalDateTime.now();
            Bid manualBid = new Bid(
                    "B-" + UUID.randomUUID().toString().substring(0, 8),
                    auction.getId(), bidderId, amount, now, BidSource.MANUAL);
            auction.addBid(manualBid);

            // ⑨ Anti-sniping
            antiSnipingService.applyExtensionIfNeeded(auction, now);

            // ⑩ AutoBid cascade — TRONG CÙNG LOCK
            autoBidService.resolveAutoBids(auction);

            // ⑪ Persist trong 1 transaction
            List<Bid> newBids = auction.getBidHistory()
                    .subList(sizeBefore, auction.getBidHistory().size());

            try (Connection conn = db.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    for (Bid b : newBids) {
                        bidDao.save(conn, b);
                    }
                    auctionDao.update(conn, auction);
                    conn.commit();
                } catch (Exception ex) {
                    try { conn.rollback(); } catch (SQLException ignore) {}
                    throw new DataAccessException("Lưu bid thất bại", ex);
                }
            } catch (SQLException e) {
                throw new DataAccessException("Không lấy được connection", e);
            }

            return new BidResult(auction,manualBid);

        } finally {
            lock.unlock();
        }
    }
}