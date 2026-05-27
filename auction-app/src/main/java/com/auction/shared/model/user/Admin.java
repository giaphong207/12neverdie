package com.auction.shared.model.user;

public final class Admin extends User {

    public Admin(String id, String username, String password) {
        super(id, username, password);
    }

    public Admin(String id, String username, String password, long balance) {
        super(id, username, password, balance);
    }
}