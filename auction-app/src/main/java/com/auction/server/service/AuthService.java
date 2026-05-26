package com.auction.server.service;

import com.auction.shared.model.user.Role;
import com.auction.shared.model.user.User;

public interface AuthService {
    User login(String username, String password);
    User register(String username, String password, Role role);
    boolean usernameExists(String username);
}
