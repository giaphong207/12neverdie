package com.auction.server.DAO;

import com.auction.shared.exception.AppExceptions.DataAccessException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Singleton quản lý kết nối DB.
 *
 * - HikariCP làm connection pool (10 connection mặc định)
 * - Đọc config từ db.properties trong classpath
 * - Có thể override bằng env var (DB_URL, DB_USER, DB_PASS)
 */
public final class Database {

    private static volatile Database instance;
    private final HikariDataSource dataSource;

    /** Constructor private — chỉ Singleton này được tạo. */
    private Database() {
        Properties props = loadProperties();

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl( envOr("DB_URL",  props.getProperty("db.url")) );
        cfg.setUsername(envOr("DB_USER", props.getProperty("db.user")));
        cfg.setPassword(envOr("DB_PASS", props.getProperty("db.password")));
        cfg.setMaximumPoolSize(
                Integer.parseInt(props.getProperty("db.pool.maxSize", "10")));
        cfg.setConnectionTimeout(
                Long.parseLong(props.getProperty("db.pool.connectionTimeout", "3000")));
        cfg.setPoolName("AuctionPool");

        this.dataSource = new HikariDataSource(cfg);
        System.out.println("[Database] Đã khởi tạo HikariCP pool xong (maxSize="
                + cfg.getMaximumPoolSize() + ")");
    }

    /** Lấy instance Singleton (thread-safe Double-Check Locking). */
    public static Database getInstance() {
        if (instance == null) {
            synchronized (Database.class) {
                if (instance == null) {
                    instance = new Database();
                }
            }
        }
        return instance;
    }

    /** Lấy 1 connection từ pool — caller phải close() khi xong. */
    public Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new DataAccessException("Không lấy được connection từ pool", e);
        }
    }

    /** Đóng pool khi server shutdown. */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("[Database] Đã đóng connection pool.");
        }
    }

    // -------- helpers --------

    private Properties loadProperties() {
        Properties p = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/db.properties")) {
            if (in == null) {
                throw new DataAccessException("Không tìm thấy file db.properties trong resources");
            }
            p.load(in);
        } catch (IOException e) {
            throw new DataAccessException("Không đọc được db.properties", e);
        }
        return p;
    }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : fallback;
    }
}