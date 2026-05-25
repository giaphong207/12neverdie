package com.auction.server.DAO;

import com.auction.shared.exception.AppExceptions.DataAccessException;
import com.auction.shared.model.Bid;
import com.auction.shared.model.BidSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation của BidDao dùng JDBC + MySQL.
 *
 * Bid là immutable (final fields) — không có UPDATE, chỉ INSERT.
 */
public class JdbcBidDao implements BidDao {

    private final Database db;

    public JdbcBidDao(Database db) {
        this.db = db;
    }

    @Override
    public List<Bid> findByAuctionId(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return new ArrayList<>();
        }

        String sql = """
                SELECT id, auction_id, bidder_id, amount, source, created_at
                  FROM bids
                 WHERE auction_id = ?
                 ORDER BY created_at ASC
                """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, auctionId);

            try (ResultSet rs = ps.executeQuery()) {
                List<Bid> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapBid(rs));
                }
                return result;
            }

        } catch (SQLException e) {
            throw new DataAccessException(
                    "findByAuctionId(" + auctionId + ") bids failed", e);
        }
    }

    @Override
    public void save(Bid bid) {
        try (Connection conn = db.getConnection()) {
            save(conn, bid);
        } catch (SQLException e) {
            throw new DataAccessException("save bid failed", e);
        }
    }

    @Override
    public void save(Connection conn, Bid bid) {
        String sql = """
                INSERT INTO bids (id, auction_id, bidder_id, amount, source, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, bid.getId());
            ps.setString(2, bid.getAuctionId());
            ps.setString(3, bid.getBidderId());
            ps.setLong  (4, bid.getAmount());
            ps.setString(5, bid.getSource().name());
            ps.setTimestamp(6, Timestamp.valueOf(bid.getCreatedAt()));

            int affected = ps.executeUpdate();
            System.out.println("[JdbcBidDao] save() affected " + affected + " row(s)");

        } catch (SQLException e) {
            throw new DataAccessException("save bid(" + bid.getId() + ") failed", e);
        }
    }

    @Override
    public int countByAuctionId(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return 0;
        }

        String sql = "SELECT COUNT(*) FROM bids WHERE auction_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, auctionId);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);   // cột đầu tiên = COUNT(*)
            }

        } catch (SQLException e) {
            throw new DataAccessException(
                    "countByAuctionId(" + auctionId + ") failed", e);
        }
    }

    /**
     * Map row ResultSet → Bid.
     * Bid có constructor 6 tham số đầy đủ (id, auctionId, bidderId, amount, createdAt, source).
     */
    private Bid mapBid(ResultSet rs) throws SQLException {
        return new Bid(
                rs.getString("id"),
                rs.getString("auction_id"),
                rs.getString("bidder_id"),
                rs.getLong("amount"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                BidSource.valueOf(rs.getString("source"))
        );
    }
}