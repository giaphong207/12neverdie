package com.auction.shared.model;

public class Bidder extends User {

    public Bidder(String id, String username, String password) {
        super(id, username, password, Role.BIDDER);
    }
}