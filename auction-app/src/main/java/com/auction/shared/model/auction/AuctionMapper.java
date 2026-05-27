package com.auction.shared.model.auction;

import com.auction.shared.model.bid.Bid;

import java.time.LocalDateTime;
import java.util.List;

public final class AuctionMapper {

    private AuctionMapper() {}   // utility class, không cho instantiate

    public static Auction fromDb(String id,
                                 String itemId,
                                 String sellerId,
                                 long startPrice,
                                 long currentPrice,
                                 long minIncrement,
                                 AuctionStatus status,
                                 LocalDateTime startTime,
                                 LocalDateTime endTime,
                                 String highestBidderId,
                                 String winnerBidderId,
                                 List<Bid> bidHistory) {
        return new Auction(
                id, itemId, sellerId,
                startPrice, currentPrice, minIncrement,
                status, startTime, endTime,
                highestBidderId, winnerBidderId,
                bidHistory
        );
    }
}
