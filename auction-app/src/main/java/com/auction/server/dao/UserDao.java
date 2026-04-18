package com.auction.server.dao;

import java.util.List;
import java.util.Optional;

import com.auction.shared.model.User;

public interface UserDao {
    List<User> findAll();
    Optional<User> findByUsername(String username);
    Optional<User> findById(String id); // Sẽ báo đỏ chỗ getId() nếu User chưa có hàm này, tạm thời kệ nó
    void save(User user);
}