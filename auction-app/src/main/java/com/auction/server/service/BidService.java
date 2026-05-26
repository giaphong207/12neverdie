package com.auction.server.service;

public interface BidService {
    BidResult placeBid(String auctionId, String bidderId, long amount);
}