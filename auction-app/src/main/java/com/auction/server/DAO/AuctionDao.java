package com.auction.server.DAO;

import com.auction.shared.model.Auction;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface AuctionDao {

    List<Auction> findAll();

    /** Load auction kèm bid history. */
    Optional<Auction> findById(String id);

    /** INSERT or UPDATE — auto-commit, tự đóng connection. */
    void save(Auction auction);

    /**
     * UPDATE auction trong transaction có sẵn (không close connection).
     * Dùng cho BidService cần INSERT bid + UPDATE auction cùng 1 transaction.
     */
    void update(Connection conn, Auction auction);

    void deleteById(String id);

    /** Lấy auction đang OPEN hoặc RUNNING. */
    List<Auction> findActiveAuctions();
}