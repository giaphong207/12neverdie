package com.auction.server.dao;

import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class FileAuctionDao implements AuctionDao {
    @Override
    public List<Auction> findAll() {
        return DataManager.getInstance().getStore().getAuctions();
    }

    @Override
    public Optional<Auction> findById(String id) {
        return findAll().stream().filter(a -> a.getId().equals(id)).findFirst();
    }

    @Override
    public void save(Auction auction) {
        AppDataStore store = DataManager.getInstance().getStore();
        store.getAuctions().removeIf(a -> a.getId().equals(auction.getId()));
        store.getAuctions().add(auction);
        DataManager.getInstance().save(store);
    }

    @Override
    public void deleteById(String id) {
        AppDataStore store = DataManager.getInstance().getStore();
        store.getAuctions().removeIf(a -> a.getId().equals(id));
        DataManager.getInstance().save(store);
    }

    @Override
    public List<Auction> findActiveAuctions() {
        // Lọc theo Enum của nhóm
        return findAll().stream()
                .filter(a -> a.getStatus() == AuctionStatus.OPEN || a.getStatus() == AuctionStatus.RUNNING)
                .collect(Collectors.toList());
    }
}