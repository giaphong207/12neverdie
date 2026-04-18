package com.auction.shared.model;

public class Seller extends User {

    public Seller(String id, String username, String password) {
        super(id, username, password, Role.SELLER);
    }
}