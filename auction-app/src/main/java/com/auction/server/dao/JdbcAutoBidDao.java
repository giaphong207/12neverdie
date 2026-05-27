package com.auction.server.dao;

import com.auction.shared.exception.AppExceptions.DataAccessException;
import com.auction.shared.model.bid.AutoBidConfig;
import com.auction.shared.model.bid.AutoBidConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation của AutoBidDao dùng JDBC + MySQL.
 */
public class JdbcAutoBidDao implements AutoBidDao {
    private static final Logger log = LoggerFactory.getLogger(JdbcAutoBidDao.class);
    private static final String COLUMNS =
            "id, auction_id, bidder_id, max_amount, increment, enabled, created_at";

    private final Database db;

    public JdbcAutoBidDao(Database db) {
        this.db = db;
    }

    @Override
    public List<AutoBidConfig> findByAuctionId(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return new ArrayList<>();
        }

        String sql = "SELECT " + COLUMNS + " FROM auto_bid_configs WHERE auction_id = ?";
        return queryList(sql, auctionId);
    }

    @Override
    public Optional<AutoBidConfig> findByAuctionIdAndBidderId(
            String auctionId, String bidderId) {
        if (auctionId == null || bidderId == null) {
            return Optional.empty();
        }

        String sql = "SELECT " + COLUMNS + " FROM auto_bid_configs "
                + "WHERE auction_id = ? AND bidder_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, auctionId);
            ps.setString(2, bidderId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapConfig(rs));
                }
                return Optional.empty();
            }

        } catch (SQLException e) {
            throw new DataAccessException(
                    "findByAuctionIdAndBidderId failed", e);
        }
    }

    @Override
    public void save(AutoBidConfig cfg) {
        // UPSERT: trùng UNIQUE(auction_id, bidder_id) thì UPDATE
        String sql = """
                INSERT INTO auto_bid_configs
                  (id, auction_id, bidder_id, max_amount, increment, enabled, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  max_amount = VALUES(max_amount),
                  increment  = VALUES(increment),
                  enabled    = VALUES(enabled)
                """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, cfg.getId());
            ps.setString(2, cfg.getAuctionId());
            ps.setString(3, cfg.getBidderId());
            ps.setLong  (4, cfg.getMaxAmount());
            ps.setLong  (5, cfg.getIncrement());
            ps.setBoolean(6, cfg.isEnabled());
            ps.setTimestamp(7, Timestamp.valueOf(cfg.getCreatedAt()));

            int affected = ps.executeUpdate();
            log.debug("save() affected {} row(s)", affected);

        } catch (SQLException e) {
            throw new DataAccessException(
                    "save autoBidConfig(" + cfg.getId() + ") failed", e);
        }
    }

    @Override
    public void deleteById(String configId) {
        if (configId == null || configId.isBlank()) return;

        String sql = "DELETE FROM auto_bid_configs WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, configId);
            int affected = ps.executeUpdate();
            log.debug("deleteById() affected {} row(s)", affected);

        } catch (SQLException e) {
            throw new DataAccessException(
                    "deleteById autoBidConfig(" + configId + ") failed", e);
        }
    }

    @Override
    public List<AutoBidConfig> findAll() {
        return queryList("SELECT " + COLUMNS + " FROM auto_bid_configs", null);
    }

    // ─────────── helpers ───────────

    private List<AutoBidConfig> queryList(String sql, String param) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (param != null) {
                ps.setString(1, param);
            }

            try (ResultSet rs = ps.executeQuery()) {
                List<AutoBidConfig> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapConfig(rs));
                }
                return result;
            }

        } catch (SQLException e) {
            throw new DataAccessException("query autoBidConfig failed", e);
        }
    }

    private AutoBidConfig mapConfig(ResultSet rs) throws SQLException {
        return AutoBidConfigMapper.fromDb(
                rs.getString("id"),
                rs.getString("auction_id"),
                rs.getString("bidder_id"),
                rs.getLong("max_amount"),
                rs.getLong("increment"),
                rs.getBoolean("enabled"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }
}