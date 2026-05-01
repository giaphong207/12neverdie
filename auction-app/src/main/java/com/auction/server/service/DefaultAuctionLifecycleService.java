package com.auction.server.service;

import com.auction.server.dao.AuctionDao;
import com.auction.shared.model.Auction;

import java.time.LocalDateTime;
import java.util.List;

public class DefaultAuctionLifecycleService implements AuctionLifecycleService {
    private final AuctionDao auctionDao;

    public DefaultAuctionLifecycleService(AuctionDao auctionDao) {
        this.auctionDao = auctionDao;
    }

    @Override
    public Auction updateStatusByTime(String auctionId) {
        Auction auction = auctionDao.findById(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy auction: " + auctionId));

        auction.updateStatusByTime(LocalDateTime.now());
        auctionDao.save(auction);
        return auction;
    }

    @Override
    public void updateAllAuctionStatuses() {
        List<Auction> auctions = auctionDao.findAll();

        for (Auction auction : auctions) {
            auction.updateStatusByTime(LocalDateTime.now());
            auctionDao.save(auction);
        }
    }

    @Override
    public void finishAuction(String auctionId) {
        Auction auction = auctionDao.findById(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy auction: " + auctionId));

        auction.finish();
        auctionDao.save(auction);
    }
}