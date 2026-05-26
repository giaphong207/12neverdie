package com.auction.shared.model.bid;

import java.time.LocalDateTime;

public class AutoBidConfigMapper {

    public static AutoBidConfig fromDb(String id,
                                       String auctionId,
                                       String bidderId,
                                       long maxAmount,
                                       long increment,
                                       boolean enabled,
                                       LocalDateTime createdAt) {
        return new AutoBidConfig(id, auctionId, bidderId,
                maxAmount, increment, enabled, createdAt);
    }
}
