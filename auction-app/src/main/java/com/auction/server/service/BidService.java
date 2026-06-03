package com.auction.server.service;

import java.util.Optional;

public interface BidService {

    BidOutcome placeBid(String auctionId, String bidderId, long amount);

    /**
     * Chạy cascade auto-bid cho phiên rồi lưu nếu phát sinh bid mới.
     * Dùng sau khi vừa thiết lập auto-bid để đặt "giá mở màn" ngay.
     *
     * @return Optional.of(outcome) nếu có ≥1 auto-bid được đặt (cần broadcast),
     *         Optional.empty() nếu không đặt gì.
     */
    Optional<BidOutcome> triggerAutoBids(String auctionId);
    /**
     * Thiết lập (tạo/cập nhật) auto-bid cho một bidder, rồi đặt "giá mở màn" ngay
     * nếu phiên còn dư địa. TOÀN BỘ luật nghiệp vụ (phiên RUNNING, đủ ví, không
     * phải người bán, maxAmount/increment > 0) kiểm ở ĐÂY và ném AppException khi
     * vi phạm — handler chỉ bắt và dịch ra response.
     *
     * @return Optional.of(outcome) nếu có auto-bid mở màn (cần broadcast), empty nếu không.
     */
    Optional<BidOutcome> setupAutoBid(String auctionId, String bidderId,
                                      long maxAmount, long increment);
}