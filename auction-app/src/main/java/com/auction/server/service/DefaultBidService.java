package com.auction.server.service;

import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.DataManager;
import com.auction.shared.exception.AuctionClosedException;
import com.auction.shared.exception.AuctionNotFoundException;
import com.auction.shared.exception.InvalidBidException;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;
import com.auction.shared.model.Bid;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public class DefaultBidService implements BidService {
    private final AuctionDao auctionDao;

    public DefaultBidService(AuctionDao auctionDao) {
        this.auctionDao = auctionDao;
    }

    @Override
    public Auction placeBid(String auctionId, String bidderId, long amount) {
        // 1. Tìm Auction
        Optional<Auction> optAuction = auctionDao.findById(auctionId);
        if (optAuction.isEmpty()) {
            throw new AuctionNotFoundException(auctionId);
        }

        Auction auction = optAuction.get();

        // ==========================================
        // TODO: CHỜ TV4 TẠO AuctionLifecycleService.
        // Tạm thời TV3 tự check thời gian để chống lỗi mất đồng bộ.
        if (auction.getEndTime() != null && LocalDateTime.now().isAfter(auction.getEndTime())) {
            if (auction.getStatus() == AuctionStatus.RUNNING) {
                auction.finish();
            }
        }
        // ==========================================

        // 2. Kiểm tra trạng thái
        if (auction.getStatus() != AuctionStatus.RUNNING) {
            throw new AuctionClosedException("Phiên đấu giá đã đóng hoặc chưa bắt đầu.");
        }

        // 3. Kiểm tra số tiền hợp lệ (dựa vào hàm canBid của Auction)
        if (!auction.canBid(amount)) {
            long required = auction.getCurrentPrice() + auction.getMinIncrement();
            throw new InvalidBidException("Số tiền đặt phải từ " + required + " VNĐ trở lên.");
        }

        // 4. Khởi tạo Bid dùng đúng Constructor của TV2
        String bidId = "B-" + UUID.randomUUID().toString().substring(0, 8);
        Bid newBid = new Bid(bidId, auction.getId(), bidderId, amount, LocalDateTime.now());

        // 5. Thêm vào Auction (tự động cập nhật currentPrice và highestBidderId)
        auction.addBid(newBid);

        // 6. Lưu xuống DB
        auctionDao.save(auction);
        try {
            // Chốt dữ liệu file .dat nếu xài DataManager
            DataManager.getInstance().save(DataManager.getInstance().getStore());
        } catch (Exception e) {
            // Bỏ qua nếu chạy trong môi trường Test không có DataManager
        }

        return auction;
    }
}