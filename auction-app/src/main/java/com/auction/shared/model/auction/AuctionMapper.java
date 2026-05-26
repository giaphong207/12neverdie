package com.auction.shared.model.auction;

import com.auction.server.DAO.AuctionEntity;

public class AuctionMapper {
    public static Auction toDomain(AuctionEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity không được null");
        }

        return new Auction(
                entity.getId(),
                entity.getItemId(),
                entity.getSellerId(),
                entity.getStartPrice(),
                entity.getCurrentPrice(),
                entity.getMinIncrement(),
                entity.getStatus(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getHighestBidderId(),
                entity.getWinnerBidderId(),
                entity.getBidHistory()
        );
    }
}
