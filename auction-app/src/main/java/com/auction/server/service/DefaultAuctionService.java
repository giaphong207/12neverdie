package com.auction.server.service;

import com.auction.server.dao.AuctionDao;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DefaultAuctionService implements AuctionService {

    private final AuctionDao auctionDao;
    private final AuctionLifecycleService lifecycleService;   // ← BẮT BUỘC

    public DefaultAuctionService(AuctionDao auctionDao,
                                 AuctionLifecycleService lifecycleService) {
        this.auctionDao = auctionDao;
        this.lifecycleService = lifecycleService;             // ← BẮT BUỘC
    }

    @Override
    public List<Auction> getActiveAuctions() {
        return auctionDao.findActiveAuctions();
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

        // Schedule tasks — KEY của refactor
        if (initialStatus == AuctionStatus.OPEN) {
            lifecycleService.scheduleStart(auction);
        }
        lifecycleService.scheduleClose(auction);

        return auction;
    }

}