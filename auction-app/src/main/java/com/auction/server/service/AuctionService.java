package com.auction.server.service;

import com.auction.shared.model.Auction;
import java.util.List;
import java.util.Optional;

public interface AuctionService {
    List<Auction> getActiveAuctions();
    Optional<Auction> getAuctionById(String auctionId);
}