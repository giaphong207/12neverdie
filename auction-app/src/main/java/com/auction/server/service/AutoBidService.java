package com.auction.server.service;

import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.bid.AutoBidConfig;

import java.util.List;

/**
 * Service xử lý logic Auto-Bidding (TV4 sở hữu - Contract 1 tuần 5).
 *
 * QUAN TRỌNG (Quy tắc 2 tuần 5):
 *   resolveAutoBids() PHẢI được gọi BÊN TRONG cùng một lock của auction
 *   với manual bid. KHÔNG gọi từ ngoài lock.
 */
public interface AutoBidService {

    /**
     * Tạo mới hoặc cập nhật cấu hình auto-bid cho 1 (auction, bidder).
     *
     * @throws IllegalArgumentException nếu maxAmount/increment <= 0.
     */
    void upsertConfig(String auctionId, String bidderId, long maxAmount, long increment);

    List<AutoBidConfig> getConfigsByAuction(String auctionId);

    /** Tắt config (không xóa). Trả về true nếu có config bị tắt. */
    boolean disableConfig(String auctionId, String bidderId);

    /**
     * Resolve toàn bộ chuỗi auto-bid sau khi có một bid hợp lệ vừa được chấp nhận.
     *
     * Trả về:
     *   true  - có ít nhất 1 auto-bid được tạo
     *   false - không có gì thay đổi
     *
     * Method KHÔNG tự save auction xuống DAO; caller chịu trách nhiệm save.
     */
    boolean resolveAutoBids(Auction auction);
}