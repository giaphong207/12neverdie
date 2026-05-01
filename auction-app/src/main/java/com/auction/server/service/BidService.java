package com.auction.server.service;

import com.auction.shared.model.Auction;

public interface BidService {
    Auction placeBid(String auctionId, String bidderId, long amount);
}