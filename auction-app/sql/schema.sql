-- ========================================================================
-- AUCTION DATABASE — SCHEMA
-- ========================================================================
-- File này tạo toàn bộ 5 bảng cho hệ thống đấu giá.
-- Chạy 1 lần khi setup máy mới.
--
-- CÁCH CHẠY (từ terminal):
--   mysql -u auction_user -p auction_db < sql/schema.sql
--
-- HOẶC trong Workbench:
--   File → Open SQL Script → chọn file này → Ctrl+Shift+Enter
-- ========================================================================

USE auction_db;

-- Drop tables nếu đã có (thứ tự ngược FK)
-- LƯU Ý: Lệnh này XÓA HẾT DATA. Bỏ qua khi chạy production.
DROP TABLE IF EXISTS auto_bid_configs;
DROP TABLE IF EXISTS bids;
DROP TABLE IF EXISTS auctions;
DROP TABLE IF EXISTS items;
DROP TABLE IF EXISTS users;

-- ========================================================================
-- 1. USERS — tài khoản người dùng
-- ========================================================================
CREATE TABLE users (
                       id          VARCHAR(36)  PRIMARY KEY,
                       username    VARCHAR(64)  NOT NULL UNIQUE,
                       password    VARCHAR(255) NOT NULL,
                       role        ENUM('BIDDER','SELLER','ADMIN') NOT NULL,
                       balance     BIGINT       NOT NULL DEFAULT 0,
                       created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================================================
-- 2. ITEMS — sản phẩm đấu giá
-- ========================================================================
CREATE TABLE items (
                       id           VARCHAR(36)  PRIMARY KEY,
                       seller_id    VARCHAR(36)  NOT NULL,
                       name         VARCHAR(255) NOT NULL,
                       description  TEXT         NOT NULL,
                       start_price  BIGINT       NOT NULL,
                       type         ENUM('ELECTRONICS','ART','VEHICLE') NOT NULL,
                       CONSTRAINT fk_items_seller
                           FOREIGN KEY (seller_id) REFERENCES users(id)
                               ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================================================
-- 3. AUCTIONS — phiên đấu giá
-- ========================================================================
CREATE TABLE auctions (
                          id                 VARCHAR(36) PRIMARY KEY,
                          item_id            VARCHAR(36) NOT NULL,
                          seller_id          VARCHAR(36) NOT NULL,
                          start_price        BIGINT      NOT NULL,
                          current_price      BIGINT      NOT NULL,
                          min_increment      BIGINT      NOT NULL,
                          status             ENUM('OPEN','RUNNING','FINISHED','PAID','CANCELED') NOT NULL,
                          start_time         DATETIME    NOT NULL,
                          end_time           DATETIME    NOT NULL,
                          highest_bidder_id  VARCHAR(36) NULL,
                          winner_bidder_id   VARCHAR(36) NULL,

                          CONSTRAINT fk_auctions_item
                              FOREIGN KEY (item_id) REFERENCES items(id)
                                  ON DELETE RESTRICT,
                          CONSTRAINT fk_auctions_seller
                              FOREIGN KEY (seller_id) REFERENCES users(id)
                                  ON DELETE RESTRICT,
                          CONSTRAINT fk_auctions_highest
                              FOREIGN KEY (highest_bidder_id) REFERENCES users(id)
                                  ON DELETE SET NULL,
                          CONSTRAINT fk_auctions_winner
                              FOREIGN KEY (winner_bidder_id) REFERENCES users(id)
                                  ON DELETE SET NULL,

                          INDEX idx_status (status),
                          INDEX idx_end_time (end_time),
                          INDEX idx_status_endtime (status, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================================================
-- 4. BIDS — lịch sử đặt giá
-- ========================================================================
CREATE TABLE bids (
                      id          VARCHAR(36)   PRIMARY KEY,
                      auction_id  VARCHAR(36)   NOT NULL,
                      bidder_id   VARCHAR(36)   NOT NULL,
                      amount      BIGINT        NOT NULL,
                      source      ENUM('MANUAL','AUTO') NOT NULL DEFAULT 'MANUAL',
                      created_at  DATETIME(3)   NOT NULL,

                      CONSTRAINT fk_bids_auction
                          FOREIGN KEY (auction_id) REFERENCES auctions(id)
                              ON DELETE CASCADE,
                      CONSTRAINT fk_bids_bidder
                          FOREIGN KEY (bidder_id) REFERENCES users(id)
                              ON DELETE RESTRICT,

                      INDEX idx_auction_time (auction_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================================================
-- 5. AUTO_BID_CONFIGS — cấu hình auto-bid của bidder
-- ========================================================================
CREATE TABLE auto_bid_configs (
                                  id          VARCHAR(36)  PRIMARY KEY,
                                  auction_id  VARCHAR(36)  NOT NULL,
                                  bidder_id   VARCHAR(36)  NOT NULL,
                                  max_amount  BIGINT       NOT NULL,
                                  increment   BIGINT       NOT NULL,
                                  enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
                                  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                  UNIQUE KEY uk_auction_bidder (auction_id, bidder_id),

                                  CONSTRAINT fk_autobid_auction
                                      FOREIGN KEY (auction_id) REFERENCES auctions(id)
                                          ON DELETE CASCADE,
                                  CONSTRAINT fk_autobid_bidder
                                      FOREIGN KEY (bidder_id) REFERENCES users(id)
                                          ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================================================
-- DONE
-- ========================================================================
-- Verify:
--   SHOW TABLES;
--   SELECT COUNT(*) FROM users;