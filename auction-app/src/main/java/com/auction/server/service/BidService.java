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
}