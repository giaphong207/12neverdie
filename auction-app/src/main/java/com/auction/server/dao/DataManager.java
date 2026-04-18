package com.auction.server.dao;

import java.io.File;

public final class DataManager {
    private static DataManager instance;
    private final String FILE_PATH = "data/database.dat";
    private AppDataStore store;

    private DataManager() {
        this.store = load();
    }

    public static DataManager getInstance() {
        if (instance == null) {
            instance = new DataManager();
        }
        return instance;
    }

    public AppDataStore load() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            System.out.println("Tạo kho dữ liệu mới rỗng...");
            return new AppDataStore();
        }
        try {
            return (AppDataStore) SerializationUtils.readObject(FILE_PATH);
        } catch (Exception e) {
            System.err.println("Lỗi đọc file, tạo kho mới: " + e.getMessage());
            return new AppDataStore();
        }
    }

    public void save(AppDataStore store) {
        try {
            SerializationUtils.writeObject(FILE_PATH, store);
            this.store = store;
            System.out.println("Đã lưu dữ liệu xuống ổ cứng!");
        } catch (Exception e) {
            System.err.println("Lỗi ghi file: " + e.getMessage());
        }
    }

    public AppDataStore getStore() {
        return this.store;
    }
}