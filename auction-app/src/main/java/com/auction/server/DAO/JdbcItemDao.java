package com.auction.server.DAO;

import com.auction.shared.exception.AppExceptions.DataAccessException;
import com.auction.shared.model.Item;
import com.auction.shared.model.ItemType;
import com.auction.shared.pattern.ItemFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation của ItemDao dùng JDBC + MySQL.
 *
 * Item là abstract class, có 3 subclass (Electronics/Art/Vehicle).
 * Dùng ItemFactory để tạo đúng subclass dựa vào cột 'type'.
 */
public class JdbcItemDao implements ItemDao {

    private static final String COLUMNS =
            "id, seller_id, name, description, start_price, type";

    private final Database db;

    public JdbcItemDao(Database db) {
        this.db = db;
    }

    @Override
    public List<Item> findAll() {
        String sql = "SELECT " + COLUMNS + " FROM items";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Item> result = new ArrayList<>();
            while (rs.next()) {
                result.add(mapItem(rs));
            }
            return result;

        } catch (SQLException e) {
            throw new DataAccessException("findAll() items failed", e);
        }
    }

    @Override
    public Optional<Item> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        String sql = "SELECT " + COLUMNS + " FROM items WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapItem(rs));
                }
                return Optional.empty();
            }

        } catch (SQLException e) {
            throw new DataAccessException("findById(" + id + ") item failed", e);
        }
    }

    @Override
    public List<Item> findBySellerId(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            return new ArrayList<>();
        }

        String sql = "SELECT " + COLUMNS + " FROM items WHERE seller_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, sellerId);

            try (ResultSet rs = ps.executeQuery()) {
                List<Item> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapItem(rs));
                }
                return result;
            }

        } catch (SQLException e) {
            throw new DataAccessException("findBySellerId(" + sellerId + ") failed", e);
        }
    }

    @Override
    public void save(Item item) {
        String sql = """
                INSERT INTO items (id, seller_id, name, description, start_price, type)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  name        = VALUES(name),
                  description = VALUES(description),
                  start_price = VALUES(start_price)
                """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, item.getId());
            ps.setString(2, item.getSellerId());
            ps.setString(3, item.getName());
            ps.setString(4, item.getDescription());
            ps.setLong  (5, item.getStartPrice());     // BIGINT → setLong
            ps.setString(6, item.getType().name());

            int affected = ps.executeUpdate();
            System.out.println("[JdbcItemDao] save() affected " + affected + " row(s)");

        } catch (SQLException e) {
            throw new DataAccessException("save item(" + item.getId() + ") failed", e);
        }
    }

    @Override
    public void deleteById(String id) {
        if (id == null || id.isBlank()) {
            return;
        }

        String sql = "DELETE FROM items WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            int affected = ps.executeUpdate();
            System.out.println("[JdbcItemDao] deleteById() affected " + affected + " row(s)");

        } catch (SQLException e) {
            throw new DataAccessException("deleteById(" + id + ") item failed", e);
        }
    }

    /**
     * Map row ResultSet → Item (đúng subclass qua ItemFactory).
     */
    private Item mapItem(ResultSet rs) throws SQLException {
        return ItemFactory.createItem(
                ItemType.valueOf(rs.getString("type")),
                rs.getString("id"),
                rs.getString("seller_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getLong("start_price")
        );
    }
}