package com.auction.server.dao;

import com.auction.shared.model.User;
import java.util.List;
import java.util.Optional;

public interface UserDao {
    
    List<User> findAll();
    Optional<User> findById(String id);
    Optional<User> findByUsername(String username);
    void save(User user);
    
}