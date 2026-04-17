package com.auction.server.service;

import com.auction.shared.model.Role;
import com.auction.shared.model.User;

public interface AuthService {
    User login(String username, String password);
    User register(String username, String password, Role role);
    boolean usernameExists(String username);
}
