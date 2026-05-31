package com.auction.shared.model.user;

import com.auction.shared.model.auction.Auction;

public final class Admin extends User {

    public Admin(String id, String username, String password) {
        super(id, username, password);
    }

    public Admin(String id, String username, String password, long balance) {
        super(id, username, password, balance);
    }

    @Override
    public boolean canManage(Auction auction) {
        return true; // admin quản trị mọi phiên
    }
}