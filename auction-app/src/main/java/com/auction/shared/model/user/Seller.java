package com.auction.shared.model.user;

import com.auction.shared.model.auction.Auction;

public final class Seller extends User {

    public Seller(String id, String username, String password) {
        super(id, username, password);
    }

    public Seller(String id, String username, String password, long balance) {
        super(id, username, password, balance);
    }

    @Override
    public boolean canManage(Auction auction) {
        return auction != null && getId().equals(auction.getSellerId()); //chỉ phiên mình
    }
}