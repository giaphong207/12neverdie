package com.auction.server.dao;

import com.auction.shared.model.Auction;
import java.util.List;
import java.util.Optional;

public interface AuctionDao {
    List<Auction> findAll();
    Optional<Auction> findById(String id);
    void save(Auction auction);
    void deleteById(String id);
    List<Auction> findActiveAuctions(); // Phục vụ lọc dữ liệu Tuần 2
}