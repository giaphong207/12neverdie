package com.auction.server.service;

public interface BidService {
    BidOutcome placeBid(String auctionId, String bidderId, long amount);
}