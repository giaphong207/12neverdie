package com.auction.server.dao;

import com.auction.shared.model.User;
import java.util.List;
import java.util.Optional;

public interface UserDao {
    List<User> findAll();
    Optional<User> findById(String id); // Sẽ báo đỏ chỗ getId() nếu User chưa có hàm này, tạm thời kệ nó
    void save(User user);
}