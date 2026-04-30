package com.auction.server.dao;

import java.util.List;
import java.util.Optional;

import com.auction.shared.model.User;

public interface UserDao {
    Optional<User> findByUsername(String username);
    List<User> findAll();
    void save(User user);
}