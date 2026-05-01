package com.auction.server.service;

import com.auction.shared.model.Auction;

public interface AuctionLifecycleService {
    Auction updateStatusByTime(String auctionId);
    void updateAllAuctionStatuses();
    void finishAuction(String auctionId);
}