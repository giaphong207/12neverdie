package com.auction.server.dao;

import com.auction.shared.exception.AppExceptions.DataAccessException;
import com.auction.shared.factory.UserFactory;
import com.auction.shared.model.user.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation của UserDao dùng JDBC + MySQL.
 *
 * Pattern dùng:
 *   - Dependency Injection: nhận Database qua constructor
 *   - Try-with-resources: tự đóng Connection, PreparedStatement, ResultSet
 *   - PreparedStatement với ? placeholder: chống SQL injection
 */
public class JdbcUserDao implements UserDao {
    private static final Logger log = LoggerFactory.getLogger(JdbcUserDao.class);
    private final Database db;

    public JdbcUserDao(Database db) {
        this.db = db;
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT id, username, password, role, balance FROM users";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<User> result = new ArrayList<>();
            while (rs.next()) {
                result.add(mapUser(rs));
            }
            return result;

        } catch (SQLException e) {
            throw new DataAccessException("findAll() failed", e);
        }
    }

    @Override
    public Optional<User> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        String sql = "SELECT id, username, password, role, balance FROM users WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapUser(rs));
                }
                return Optional.empty();
            }

        } catch (SQLException e) {
            throw new DataAccessException("findById(" + id + ") failed", e);
        }
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        String sql = "SELECT id, username, password, role, balance FROM users WHERE username = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapUser(rs));
                }
                return Optional.empty();
            }

        } catch (SQLException e) {
            throw new DataAccessException("findByUsername(" + username + ") failed", e);
        }
    }

    @Override
    public void save(User user) {
        // Dùng MySQL UPSERT: nếu id đã tồn tại → UPDATE, chưa có → INSERT
        String sql = """
                INSERT INTO users (id, username, password, role, balance)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  username = VALUES(username),
                  password = VALUES(password),
                  role     = VALUES(role),
                  balance  = VALUES(balance)
                """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getId());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPassword());
            ps.setString(4, UserFactory.toRole(user).name());
            ps.setLong(5, user.getBalance());

            int affected = ps.executeUpdate();
            log.debug("save() affected {} row(s)", affected);

        } catch (SQLException e) {
            throw new DataAccessException("save(" + user.getUsername() + ") failed", e);
        }
    }

    @Override
    public long updateBalance(String userId, long newBalance) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId rỗng");
        }
        if (newBalance < 0) {
            throw new IllegalArgumentException("balance không được âm");
        }
        String sql = "UPDATE users SET balance = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, newBalance);
            ps.setString(2, userId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Không tìm thấy user id=" + userId);
            }
            return newBalance;
        } catch (SQLException e) {
            throw new DataAccessException("updateBalance(" + userId + ") failed", e);
        }
    }

    /**
     * Helper: map 1 row trong ResultSet thành object User đúng subclass.
     * Dựa vào cột 'role' để tạo Bidder/Seller/Admin.
     */
    /**
     * Helper: map 1 row trong ResultSet thành object User đúng subclass.
     * Delegate cho UserFactory để có 1 nguồn duy nhất tạo User từ Role
     * (tránh duplicate logic với UserFactory.createUser()).
     */
    private User mapUser(ResultSet rs) throws SQLException {
        return UserFactory.createUser(
                Role.valueOf(rs.getString("role")),
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getLong("balance")
        );
    }
}