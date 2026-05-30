package com.auction.server.service;

import com.auction.shared.model.user.Role;
import com.auction.shared.model.user.User;

import java.util.List;

public interface AuthService {
    User login(String username, String password);
    User register(String username, String password, Role role);
    boolean usernameExists(String username);
    List<User> getAllUsers();
}
