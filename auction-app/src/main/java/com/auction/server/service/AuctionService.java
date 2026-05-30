package com.auction.server.service;

import com.auction.shared.model.auction.Auction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuctionService {
    // Query (đã có)
    List<Auction> getActiveAuctions();
    /** Tất cả phiên (gồm cả FINISHED/PAID/CANCELED) — dùng cho snapshot danh sách. */
    List<Auction> getAllAuctions();
    Optional<Auction> getAuctionById(String auctionId);

    // Command (THÊM MỚI)
    Auction createAuction(String sellerId, String itemId,
                          long startPrice, long minIncrement,
                          LocalDateTime startTime, LocalDateTime endTime);
}