package com.auction.server.dao;

import com.auction.shared.model.Bid;

import java.sql.Connection;
import java.util.List;

/**
 * DAO cho bảng 'bids'.
 *
 * Lưu ý: trong project hiện tại, Auction.bidHistory cũng lưu bid.
 * Với MySQL, bid được lưu CHÍNH THỨC trong bảng riêng này.
 * Khi load Auction → JdbcAuctionDao gọi bidDao.findByAuctionId() để
 * khôi phục bidHistory.
 */
public interface BidDao {

    /** Lấy tất cả bid của 1 auction, sắp xếp theo thời gian tăng dần. */
    List<Bid> findByAuctionId(String auctionId);

    /** Insert 1 bid (auto-commit). */
    void save(Bid bid);

    /**
     * Insert 1 bid trong transaction có sẵn.
     * Dùng cho BidService khi cần INSERT bid + UPDATE auction trong 1 transaction.
     */
    void save(Connection conn, Bid bid);

    /** Đếm số bid của 1 auction. */
    int countByAuctionId(String auctionId);
}