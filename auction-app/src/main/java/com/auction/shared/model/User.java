package com.auction.shared.model;

import java.io.Serializable;

public abstract class User implements Serializable {
    private final String id;
    private final String username;
    private String password;
    private final Role role;

    protected User(String id, String username, String password, Role role) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Phải có ID");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Phải có Username");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Phải có Password");
        }
        if (role == null) {
            throw new IllegalArgumentException("Phải có Role");
        }

        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
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

    public Role getRole() {
        return role;
    }

    public void changePassword(String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Phải có mật khẩu mới");
        }
        this.password = newPassword;
    }

    @Override
    public String toString() {
        return "User{" +
                "id: '" + id + '\'' +
                ", username: " + username + '\'' +
                ", role: " + role +
                '}';
    }
}