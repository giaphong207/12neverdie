-- ========================================================================
-- SEED DEMO DATA
-- Chạy SAU schema.sql để có data thử nghiệm.
-- ========================================================================

USE auction_db;

-- Clear data cũ (giữ schema)
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE auto_bid_configs;
TRUNCATE TABLE bids;
TRUNCATE TABLE auctions;
TRUNCATE TABLE items;
TRUNCATE TABLE users;
SET FOREIGN_KEY_CHECKS = 1;

-- ========================================================================
-- USERS (4 user: 1 admin, 1 seller, 2 bidder)
-- LƯU Ý: password ở đây là plain text 'pwd_demo' để dễ test.
-- Khi áp BCrypt ở Phase 9, hash sẽ khác.
-- ========================================================================
INSERT INTO users (id, username, password, role) VALUES
                                                     ('u-admin-001',  'admin',   'admin123', 'ADMIN'),
                                                     ('u-seller-001', 'seller1', 'pwd_demo', 'SELLER'),
                                                     ('u-bidder-001', 'bidder1', 'pwd_demo', 'BIDDER'),
                                                     ('u-bidder-002', 'bidder2', 'pwd_demo', 'BIDDER');

-- ========================================================================
-- ITEMS (3 sản phẩm khác loại)
-- ========================================================================
INSERT INTO items (id, seller_id, name, description, start_price, type) VALUES
                                                                            ('i-001', 'u-seller-001', 'iPhone 15 Pro Max',  'Mới 99%, fullbox, bảo hành 6 tháng', 28000000, 'ELECTRONICS'),
                                                                            ('i-002', 'u-seller-001', 'Tranh Đông Hồ gốc',  'Bản gốc 1985, có chứng nhận',         5000000, 'ART'),
                                                                            ('i-003', 'u-seller-001', 'Honda Wave Alpha',   'Đời 2020, đi 5000km, biển HN',       15000000, 'VEHICLE');

-- ========================================================================
-- AUCTIONS (3 phiên: 1 RUNNING bình thường, 1 sắp hết hạn, 1 OPEN)
-- ========================================================================
INSERT INTO auctions (id, item_id, seller_id, start_price, current_price, min_increment, status, start_time, end_time) VALUES
                                                                                                                           ('a-001', 'i-001', 'u-seller-001', 28000000, 28000000,  500000, 'RUNNING', NOW() - INTERVAL 5 MINUTE,  NOW() + INTERVAL 1 HOUR),
                                                                                                                           ('a-002', 'i-002', 'u-seller-001',  5000000,  5000000,  100000, 'RUNNING', NOW() - INTERVAL 30 MINUTE, NOW() + INTERVAL 2 MINUTE),  -- sắp hết để test anti-sniping
                                                                                                                           ('a-003', 'i-003', 'u-seller-001', 15000000, 15000000, 1000000, 'OPEN',    NOW() + INTERVAL 30 MINUTE, NOW() + INTERVAL 3 HOUR);

-- ========================================================================
-- DONE
-- ========================================================================
-- Verify:
SELECT 'users' AS t, COUNT(*) FROM users
UNION ALL SELECT 'items',    COUNT(*) FROM items
UNION ALL SELECT 'auctions', COUNT(*) FROM auctions;