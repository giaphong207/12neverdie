package com.auction.server.dao;

import java.io.File;

import com.auction.server.seed.DemoDataSeeder;
import com.auction.shared.model.Auction;
import com.auction.shared.model.Item;
import com.auction.shared.model.User;

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
            AppDataStore newStore = new AppDataStore();
            seedIfMissing(newStore);
            return newStore;
        }
        try {
            AppDataStore loadedStore = (AppDataStore) SerializationUtils.readObject(FILE_PATH);

            // Backward compatible: file cũ chưa có autoBidConfigs
            if (loadedStore.getAutoBidConfigs() == null) {
                loadedStore.setAutoBidConfigs(new java.util.ArrayList<>());
            }

            // Seed nếu chưa có data
            if (loadedStore.getAuctions() == null || loadedStore.getAuctions().isEmpty()) {
                seedIfMissing(loadedStore);
            }

            return loadedStore;
        } catch (Exception e) {
            System.err.println("Lỗi đọc file, tạo kho mới: " + e.getMessage());
            AppDataStore fallbackStore = new AppDataStore();
            seedIfMissing(fallbackStore);
            return fallbackStore;
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

    /**
     * Seed dữ liệu demo dùng DemoDataSeeder — 4 scenario tuần 5.
     * Chỉ seed khi users rỗng.
     */
    private void seedIfMissing(AppDataStore targetStore) {
        System.out.println("Đang seed demo data (4 scenario tuần 5)...");

        for (User user : DemoDataSeeder.allUsers()) {
            targetStore.getUsers().add(user);
        }

        for (Item item : DemoDataSeeder.allItems()) {
            targetStore.getItems().add(item);
        }

        for (Auction auction : DemoDataSeeder.allAuctions()) {
            targetStore.getAuctions().add(auction);
        }

        save(targetStore);
        System.out.println("Seed xong: " + targetStore.getUsers().size()
                + " users, " + targetStore.getItems().size()
                + " items, " + targetStore.getAuctions().size() + " auctions.");
    }

    /**
     * Xóa toàn bộ data và seed lại.
     * Gọi trước khi demo anti-sniping để endTime được tính lại từ now.
     * Cách dùng: DataManager.getInstance().resetAndReseed();
     */
    public void resetAndReseed() {
        System.out.println("Reset và seed lại toàn bộ data...");
        this.store = new AppDataStore();
        seedIfMissing(this.store);
    }

    public AppDataStore getStore() {
        return this.store;
    }
}