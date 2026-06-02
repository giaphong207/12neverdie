package com.auction.server.dao;

import java.util.List;
import java.util.Optional;

import com.auction.shared.model.user.User;

public interface UserDao {
    List<User> findAll();
    Optional<User> findByUsername(String username);
    Optional<User> findById(String id);
    void save(User user);

    /** Cập nhật chỉ cột balance — trả về balance mới đã ghi. */
    long updateBalance(String userId, long newBalance);
    /** Atomic: cộng delta vào balance (delta có thể âm). Trả về balance mới. */
    long addBalance(String userId, long delta);
        // fallback không-atomic cho test double; JdbcUserDao override bằng SQL tương đối
    /** Atomic + transaction: trừ amount của from, cộng cho to. false nếu from không đủ tiền. */
    boolean transfer(String fromId, String toId, long amount);
        // fallback cho test double; JdbcUserDao override bằng 1 transaction thật

}