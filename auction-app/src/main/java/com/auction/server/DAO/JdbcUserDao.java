package com.auction.server.DAO;

import com.auction.shared.exception.AppExceptions.DataAccessException;
import com.auction.shared.model.Admin;
import com.auction.shared.model.Bidder;
import com.auction.shared.model.Role;
import com.auction.shared.model.Seller;
import com.auction.shared.model.User;

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

    private final Database db;

    public JdbcUserDao(Database db) {
        this.db = db;
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT id, username, password, role FROM users";

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

        String sql = "SELECT id, username, password, role FROM users WHERE id = ?";

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

        String sql = "SELECT id, username, password, role FROM users WHERE username = ?";

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
                INSERT INTO users (id, username, password, role)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  username = VALUES(username),
                  password = VALUES(password),
                  role     = VALUES(role)
                """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getId());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPassword());
            ps.setString(4, user.getRole().name());

            int affected = ps.executeUpdate();
            System.out.println("[JdbcUserDao] save() affected " + affected + " row(s)");

        } catch (SQLException e) {
            throw new DataAccessException("save(" + user.getUsername() + ") failed", e);
        }
    }

    /**
     * Helper: map 1 row trong ResultSet thành object User đúng subclass.
     * Dựa vào cột 'role' để tạo Bidder/Seller/Admin.
     */
    private User mapUser(ResultSet rs) throws SQLException {
        String id       = rs.getString("id");
        String username = rs.getString("username");
        String password = rs.getString("password");
        Role role       = Role.valueOf(rs.getString("role"));

        return switch (role) {
            case BIDDER -> new Bidder(id, username, password);
            case SELLER -> new Seller(id, username, password);
            case ADMIN  -> new Admin(id, username, password);
        };
    }
}