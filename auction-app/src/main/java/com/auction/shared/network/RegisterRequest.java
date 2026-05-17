package com.auction.shared.network;

import com.auction.shared.model.Role;
import java.io.Serializable;

public class RegisterRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private final String password;
    private final Role role;

    public RegisterRequest(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public Role getRole() { return role; }
}