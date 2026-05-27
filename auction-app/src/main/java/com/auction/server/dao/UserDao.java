package com.auction.server.dao;

import java.util.List;
import java.util.Optional;

import com.auction.shared.model.user.User;

public interface UserDao {
    List<User> findAll();
    Optional<User> findByUsername(String username);
    Optional<User> findById(String id);
    void save(User user);
}