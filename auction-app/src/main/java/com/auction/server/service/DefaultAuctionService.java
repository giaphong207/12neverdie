package com.auction.server.service;

import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.FileAuctionDao;
import com.auction.shared.model.Auction;
import java.util.List;
import java.util.Optional;

public class DefaultAuctionService implements AuctionService {
    private final AuctionDao auctionDao = new FileAuctionDao();

    @Override
    public List<Auction> getActiveAuctions() {
        return auctionDao.findActiveAuctions();
    }

    @Override
    public Optional<Auction> getAuctionById(String auctionId) {
        return auctionDao.findById(auctionId);
    }
}