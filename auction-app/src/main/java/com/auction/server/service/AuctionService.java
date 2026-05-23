package com.auction.server.service;

import com.auction.shared.model.Auction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuctionService {
    // Query (đã có)
    List<Auction> getActiveAuctions();
    Optional<Auction> getAuctionById(String auctionId);

    // Command (THÊM MỚI)
    Auction createAuction(String sellerId, String itemId,
                          long startPrice, long minIncrement,
                          LocalDateTime startTime, LocalDateTime endTime);
}