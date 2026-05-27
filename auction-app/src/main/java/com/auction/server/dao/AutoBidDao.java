package com.auction.server.dao;

import com.auction.shared.model.bid.AutoBidConfig;

import java.util.List;
import java.util.Optional;

/**
 * DAO cho AutoBidConfig.
 * Mỗi bidder chỉ có TỐI ĐA 1 config / 1 auction.
 */
public interface AutoBidDao {

    List<AutoBidConfig> findByAuctionId(String auctionId);

    Optional<AutoBidConfig> findByAuctionIdAndBidderId(String auctionId, String bidderId);

    void save(AutoBidConfig config);

    void deleteById(String configId);

    List<AutoBidConfig> findAll();
}