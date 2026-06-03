package com.auction.server.service;

import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.bid.AutoBidConfig;

import java.util.List;
import java.util.function.ToLongFunction;

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
     * PHẢI gọi BÊN TRONG cùng một lock của auction với manual bid.
     *
     * @param balanceOf hàm tra số dư ví hiện tại của bidder. Cascade dùng trần
     *                  hiệu dụng = min(maxAmount, balanceOf(bidderId)) để không
     *                  bao giờ đặt auto-bid vượt số dư THẬT (Fix #1).
     * @return true nếu có ≥1 auto-bid được tạo. KHÔNG tự save; caller lo save.
     */
    boolean resolveAutoBids(Auction auction, ToLongFunction<String> balanceOf);
}