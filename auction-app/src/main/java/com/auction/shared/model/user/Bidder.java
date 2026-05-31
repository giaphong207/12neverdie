package com.auction.shared.model.user;

import com.auction.shared.model.auction.Auction;

public final class Bidder extends User {

    public Bidder(String id, String username, String password) {
        super(id, username, password);
    }

    public Bidder(String id, String username, String password, long balance) {
        super(id, username, password, balance);
    }

    @Override
    public boolean canManage(Auction auction) {
        return false; //bidder không có quyền quản trị phiên
    }
}