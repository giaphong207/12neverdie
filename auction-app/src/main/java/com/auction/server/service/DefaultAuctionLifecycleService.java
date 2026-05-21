package com.auction.server.service;

import com.auction.server.dao.AuctionDao;
import com.auction.server.realtime.EventBroadcaster;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;
import com.auction.shared.network.AuctionStartedEvent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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