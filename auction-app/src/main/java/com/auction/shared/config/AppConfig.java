package com.auction.shared.config;

public final class AppConfig {
    public static final String DATA_FILE = "data/database.dat";
    public static final int SERVER_PORT = 9999;

    // ── Anti-sniping constants (TV3 thêm vào) ──────────────────────────────
    // Nếu có bid khi còn <= 30 giây thì gia hạn thêm 60 giây
    public static final long ANTI_SNIPING_TRIGGER_SECONDS  = 30;
    public static final long ANTI_SNIPING_EXTENSION_SECONDS = 60;
    // ────────────────────────────────────────────────────────────────────────

    private AppConfig() {}
}