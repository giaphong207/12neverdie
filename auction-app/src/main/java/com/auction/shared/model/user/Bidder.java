package com.auction.shared.model.user;

public final class Bidder extends User {

    public Bidder(String id, String username, String password) {
        super(id, username, password);
    }

    public Bidder(String id, String username, String password, long balance) {
        super(id, username, password, balance);
    }
}