package com.auction.server.service;

import com.auction.shared.model.Auction;
import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.FileAuctionDao;
import java.util.List;
import java.util.Optional;

public final class AuctionManager {
    private static AuctionManager instance;
    private final AuctionDao auctionDao;

    private AuctionManager() {
        this.auctionDao = new FileAuctionDao();
    }

    public static AuctionManager getInstance() {
        if (instance == null) {
            instance = new AuctionManager();
        }
        return instance;
    }

    public void addAuction(Auction auction) {
        auctionDao.save(auction);
    }

    public Optional<Auction> findById(String auctionId) {
        return auctionDao.findById(auctionId);
    }

    public List<Auction> getAllAuctions() {
        return auctionDao.findAll();
    }
}