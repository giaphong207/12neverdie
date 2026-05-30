package com.auction.server.service;

import com.auction.server.dao.AuctionDao;
import com.auction.server.realtime.EventBroadcaster;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;
import com.auction.shared.networkMessage.AuctionEvents.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DefaultAuctionService implements AuctionService {

    private final AuctionDao auctionDao;
    private final AuctionLifecycleService lifecycleService;
    private final EventBroadcaster broadcaster;
    public DefaultAuctionService(AuctionDao auctionDao,
                                 AuctionLifecycleService lifecycleService,
                                 EventBroadcaster broadcaster) {
        this.auctionDao = auctionDao;
        this.lifecycleService = lifecycleService;
        this.broadcaster = broadcaster;
    }

    @Override
    public List<Auction> getActiveAuctions() {
        return auctionDao.findActiveAuctions();
    }

    @Override
    public List<Auction> getAllAuctions() {
        return auctionDao.findAll();
    }

    @Override
    public Optional<Auction> getAuctionById(String auctionId) {
        return auctionDao.findById(auctionId);
    }

    @Override
    public Auction createAuction(String sellerId, String itemId,
                                 long startPrice, long minIncrement,
                                 LocalDateTime startTime, LocalDateTime endTime) {
        // Quyết định status ban đầu
        LocalDateTime now = LocalDateTime.now();
        AuctionStatus initialStatus = now.isBefore(startTime)
                ? AuctionStatus.OPEN
                : AuctionStatus.RUNNING;

        Auction auction = new Auction(
                "A-" + UUID.randomUUID().toString().substring(0, 8),
                itemId, sellerId, startPrice, minIncrement,
                initialStatus, startTime, endTime);

        auctionDao.save(auction);

        if (initialStatus == AuctionStatus.OPEN) {
            lifecycleService.scheduleStart(auction);
        }
        lifecycleService.scheduleClose(auction);

// Báo client: có auction mới, refresh danh sách
        broadcaster.broadcast(new AuctionCreatedEvent(auction));

        return auction;
    }
}