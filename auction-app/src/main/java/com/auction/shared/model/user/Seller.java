package com.auction.shared.model.user;

public final class Seller extends User {

    public Seller(String id, String username, String password) {
        super(id, username, password);
    }

    public Seller(String id, String username, String password, long balance) {
        super(id, username, password, balance);
    }
}