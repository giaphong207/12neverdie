-- Migration 001: thêm cột balance vào bảng users (ví tiền)
-- Chạy 1 lần nếu DB đã setup trước khi có feature ví:
--   mysql -u auction_user -p auction_db < sql/migration_001_balance.sql

ALTER TABLE users
    ADD COLUMN balance BIGINT NOT NULL DEFAULT 0
    AFTER role;
