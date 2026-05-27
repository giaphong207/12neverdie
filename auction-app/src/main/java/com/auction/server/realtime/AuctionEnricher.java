package com.auction.server.realtime;

import com.auction.server.dao.ItemDao;
import com.auction.server.dao.UserDao;
import com.auction.shared.model.auction.Auction;

/**
 * Fill các display field (itemName, itemDescription, sellerName, highestBidderName)
 * cho Auction trước khi gửi qua wire — client không phải lookup riêng.
 */
public class AuctionEnricher {

    private final ItemDao itemDao;
    private final UserDao userDao;

    public AuctionEnricher(ItemDao itemDao, UserDao userDao) {
        this.itemDao = itemDao;
        this.userDao = userDao;
    }

    public void enrich(Auction auction) {
        if (auction == null) return;

        itemDao.findById(auction.getItemId()).ifPresent(item -> {
            auction.setItemName(item.getName());
            auction.setItemDescription(item.getDescription());
        });

        userDao.findById(auction.getSellerId()).ifPresent(u ->
                auction.setSellerName(u.getUsername()));

        String highestId = auction.getHighestBidderId();
        if (highestId != null && !highestId.isBlank()) {
            userDao.findById(highestId).ifPresent(u ->
                    auction.setHighestBidderName(u.getUsername()));
        } else {
            auction.setHighestBidderName(null);
        }
    }
}
