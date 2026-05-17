package com.auction.server.dao;

import com.auction.shared.exception.DataAccessException;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation của AuctionDao dùng JDBC + MySQL.
 *
 * Điểm phức tạp nhất:
 *  - Auction có bidHistory (List<Bid>) → khi load phải gọi BidDao để load bid
 *  - Auction có cột NULLABLE (highest_bidder_id, winner_bidder_id) → cần xử lý null
 *  - Hỗ trợ transaction qua method update(Connection, Auction)
 */
public class JdbcAuctionDao implements AuctionDao {

    private static final String COLUMNS = """
            id, item_id, seller_id, start_price, current_price, min_increment,
            status, start_time, end_time, highest_bidder_id, winner_bidder_id
            """;

    private final Database db;
    private final BidDao bidDao;    // để load bid history khi findById

    public JdbcAuctionDao(Database db, BidDao bidDao) {
        this.db = db;
        this.bidDao = bidDao;
    }

    @Override
    public List<Auction> findAll() {
        return queryList("SELECT " + COLUMNS + " FROM auctions");
    }

    @Override
    public List<Auction> findActiveAuctions() {
        return queryList(
                "SELECT " + COLUMNS + " FROM auctions "
                        + "WHERE status IN ('OPEN','RUNNING') ORDER BY end_time");
    }

    @Override
    public Optional<Auction> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        String sql = "SELECT " + COLUMNS + " FROM auctions WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Auction a = mapAuction(rs);
                // Load bid history riêng
                a.restoreBidHistory(bidDao.findByAuctionId(id));
                return Optional.of(a);
            }

        } catch (SQLException e) {
            throw new DataAccessException("findById(" + id + ") auction failed", e);
        }
    }

    @Override
    public void save(Auction a) {
        String sql = """
                INSERT INTO auctions
                  (id, item_id, seller_id, start_price, current_price, min_increment,
                   status, start_time, end_time, highest_bidder_id, winner_bidder_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  current_price     = VALUES(current_price),
                  min_increment     = VALUES(min_increment),
                  status            = VALUES(status),
                  start_time        = VALUES(start_time),
                  end_time          = VALUES(end_time),
                  highest_bidder_id = VALUES(highest_bidder_id),
                  winner_bidder_id  = VALUES(winner_bidder_id)
                """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            bindAuction(ps, a);
            int affected = ps.executeUpdate();
            System.out.println("[JdbcAuctionDao] save() affected " + affected + " row(s)");

        } catch (SQLException e) {
            throw new DataAccessException("save auction(" + a.getId() + ") failed", e);
        }
    }

    @Override
    public void update(Connection conn, Auction a) {
        // CHỈ UPDATE — dùng trong transaction
        String sql = """
                UPDATE auctions SET
                  current_price     = ?,
                  status            = ?,
                  end_time          = ?,
                  highest_bidder_id = ?,
                  winner_bidder_id  = ?
                WHERE id = ?
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong  (1, a.getCurrentPrice());
            ps.setString(2, a.getStatus().name());
            ps.setTimestamp(3, Timestamp.valueOf(a.getEndTime()));
            setStringOrNull(ps, 4, a.getHighestBidderId());
            setStringOrNull(ps, 5, a.getWinnerBidderId());
            ps.setString(6, a.getId());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException(
                        "Auction " + a.getId() + " không tồn tại để update");
            }
        } catch (SQLException e) {
            throw new DataAccessException("update auction(" + a.getId() + ") failed", e);
        }
    }

    @Override
    public void deleteById(String id) {
        if (id == null || id.isBlank()) return;

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);   // bắt đầu transaction
            try {
                // Xóa bids + auto_bid_configs trước (vì FK CASCADE đã lo,
                // nhưng làm explicit để đảm bảo)
                try (PreparedStatement ps1 = conn.prepareStatement(
                        "DELETE FROM bids WHERE auction_id = ?")) {
                    ps1.setString(1, id);
                    ps1.executeUpdate();
                }
                try (PreparedStatement ps2 = conn.prepareStatement(
                        "DELETE FROM auto_bid_configs WHERE auction_id = ?")) {
                    ps2.setString(1, id);
                    ps2.executeUpdate();
                }
                try (PreparedStatement ps3 = conn.prepareStatement(
                        "DELETE FROM auctions WHERE id = ?")) {
                    ps3.setString(1, id);
                    ps3.executeUpdate();
                }
                conn.commit();
                System.out.println("[JdbcAuctionDao] deleteById(" + id + ") OK");
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            throw new DataAccessException("deleteById auction(" + id + ") failed", e);
        }
    }

    // ─────────── helpers ───────────

    private List<Auction> queryList(String sql) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Auction> result = new ArrayList<>();
            while (rs.next()) {
                Auction a = mapAuction(rs);
                // Load bid history cho mỗi auction
                a.restoreBidHistory(bidDao.findByAuctionId(a.getId()));
                result.add(a);
            }
            return result;

        } catch (SQLException e) {
            throw new DataAccessException("query auctions failed", e);
        }
    }

    /**
     * Map 1 row ResultSet → Auction.
     * Vì constructor gốc ép currentPrice = startPrice và status mặc định,
     * ta dùng restoreState() + restoreStatus() để gán giá trị thật từ DB.
     */
    private Auction mapAuction(ResultSet rs) throws SQLException {
        // Constructor cần status truyền vào — dùng tạm RUNNING, sẽ restore lại
        Auction a = new Auction(
                rs.getString("id"),
                rs.getString("item_id"),
                rs.getString("seller_id"),
                rs.getLong("start_price"),
                rs.getLong("min_increment"),
                AuctionStatus.RUNNING,                    // sẽ restore
                rs.getTimestamp("start_time").toLocalDateTime(),
                rs.getTimestamp("end_time").toLocalDateTime()
        );

        // Restore status đúng từ DB
        a.restoreStatus(AuctionStatus.valueOf(rs.getString("status")));

        // Restore state (currentPrice, highest/winner bidder)
        a.restoreState(
                rs.getLong("current_price"),
                rs.getString("highest_bidder_id"),    // có thể null
                rs.getString("winner_bidder_id")      // có thể null
        );

        return a;
    }

    /**
     * Bind 11 cột của Auction vào PreparedStatement cho INSERT.
     */
    private void bindAuction(PreparedStatement ps, Auction a) throws SQLException {
        ps.setString(1, a.getId());
        ps.setString(2, a.getItemId());
        ps.setString(3, a.getSellerId());
        ps.setLong  (4, a.getStartPrice());
        ps.setLong  (5, a.getCurrentPrice());
        ps.setLong  (6, a.getMinIncrement());
        ps.setString(7, a.getStatus().name());
        ps.setTimestamp(8, Timestamp.valueOf(a.getStartTime()));
        ps.setTimestamp(9, Timestamp.valueOf(a.getEndTime()));
        setStringOrNull(ps, 10, a.getHighestBidderId());
        setStringOrNull(ps, 11, a.getWinnerBidderId());
    }

    /**
     * Helper xử lý setString cho cột nullable.
     * Nếu value null → ps.setNull(...) thay vì setString("null") (sai!).
     */
    private void setStringOrNull(PreparedStatement ps, int index, String value)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }
}