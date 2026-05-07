package com.auction.server.service;

import com.auction.server.concurrency.AuctionLockManager;
import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.DataManager;
import com.auction.shared.exception.AuctionClosedException;
import com.auction.shared.exception.AuctionNotFoundException;
import com.auction.shared.exception.InvalidBidException;
import com.auction.shared.model.Auction;
import com.auction.shared.model.Bid;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultBidService implements BidService {

    private final AuctionDao             auctionDao;
    private final AuctionLifecycleService lifecycleService;
    private final AuctionLockManager     auctionLockManager;
    private final AntiSnipingService     antiSnipingService;   // TV3 thêm
    // TV4 sẽ thêm: private final AutoBidService autoBidService;

    // ── Constructor TV3 dùng (TV4 sẽ thêm AutoBidService vào sau) ──────────
    public DefaultBidService(AuctionDao auctionDao,
                             AuctionLifecycleService lifecycleService,
                             AuctionLockManager auctionLockManager) {
        this(auctionDao, lifecycleService, auctionLockManager,
                new DefaultAntiSnipingService());
    }

    // ── Constructor đầy đủ để test và để TV4 inject AutoBidService ──────────
    public DefaultBidService(AuctionDao auctionDao,
                             AuctionLifecycleService lifecycleService,
                             AuctionLockManager auctionLockManager,
                             AntiSnipingService antiSnipingService) {
        this.auctionDao         = auctionDao;
        this.lifecycleService   = lifecycleService;
        this.auctionLockManager = auctionLockManager;
        this.antiSnipingService = antiSnipingService;
    }

    @Override
    public Auction placeBid(String auctionId, String bidderId, long amount) {

        ReentrantLock lock = auctionLockManager.getLock(auctionId);
        lock.lock();

        try {
            // Bước 1: Tìm auction
            Optional<Auction> optAuction = auctionDao.findById(auctionId);
            if (optAuction.isEmpty()) {
                throw new AuctionNotFoundException(auctionId);
            }

            // Bước 2: Cập nhật trạng thái theo thời gian (OPEN→RUNNING→FINISHED)
            Auction auction = lifecycleService.updateStatusByTime(auctionId);

            // Bước 3: Kiểm tra phiên còn đang chạy không
            if (!auction.isRunning()) {
                throw new AuctionClosedException("Phiên đấu giá đã đóng hoặc chưa bắt đầu.");
            }

            // Bước 4: Kiểm tra số tiền có hợp lệ không
            if (!auction.canAcceptBid(amount)) {
                long required = auction.getCurrentPrice() + auction.getMinIncrement();
                throw new InvalidBidException("Số tiền đặt phải từ " + required + " VNĐ trở lên.");
            }

            // Bước 5: Tạo Bid và chấp nhận vào auction
            String bidId = "B-" + UUID.randomUUID().toString().substring(0, 8);
            Bid newBid = new Bid(bidId, auction.getId(), bidderId, amount, LocalDateTime.now());
            auction.addBid(newBid);

            // Bước 6: Anti-sniping — kiểm tra có cần gia hạn thời gian không
            // (TV3) Ghi lại thời điểm bid xảy ra TRƯỚC khi gọi để tính đúng remaining
            LocalDateTime bidTime = newBid.getCreatedAt();
            boolean extended = antiSnipingService.applyExtensionIfNeeded(auction, bidTime);
            if (extended) {
                System.out.println("[BidService] Đã gia hạn phiên " + auctionId);
            }

            // Bước 7: Auto-bid cascade — TV4 thêm dòng này:
            // autoBidService.resolveAutoBids(auction);

            // Bước 8: Lưu auction (với endTime mới nếu có gia hạn, và toàn bộ bid history)
            auctionDao.save(auction);

            try {
                DataManager.getInstance().save(DataManager.getInstance().getStore());
            } catch (Exception e) {
                // Bỏ qua lỗi DataManager khi chạy test
            }

            return auction;   // trả về auction đã có endTime mới + bid mới

        } finally {
            lock.unlock();    // luôn unlock dù có lỗi hay không
        }
    }
}