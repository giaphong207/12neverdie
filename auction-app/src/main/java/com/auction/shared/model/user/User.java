package com.auction.shared.model.user;

import java.io.Serializable;

public sealed abstract class User implements Serializable permits Admin, Seller, Bidder {
    private static final long serialVersionUID = 2L;

    private final String id;
    private final String username;
    private String password;
    private long balance;

    protected User(String id, String username, String password) {
        this(id, username, password, 0L);
    }

    protected User(String id, String username, String password, long balance) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Phải có ID");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Phải có Username");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Phải có Password");
        }

        this.id = id;
        this.username = username;
        this.password = password;
        this.balance = balance;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }

    public void changePassword(String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Phải là mật khẩu mới");
        }
        this.password = newPassword;
    }

    @Override
    public String toString() {
        return "User{" +
                "id: '" + id + '\'' +
                ", username: " + username + '\'' +
                '}';
    }
}