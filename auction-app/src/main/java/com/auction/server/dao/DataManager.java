package com.auction.server.dao;

import java.io.File;
import java.time.LocalDateTime;

// Import đầy đủ các model cần thiết
import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;
import com.auction.shared.model.Bidder;
import com.auction.shared.model.ElectronicsItem;
import com.auction.shared.model.Seller;
import com.auction.shared.model.VehicleItem;

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
            seedIfMissing(newStore); // Bơm dữ liệu nếu file chưa có
            return newStore;
        }
        try {
            AppDataStore loadedStore = (AppDataStore) SerializationUtils.readObject(FILE_PATH);
            // Kiểm tra xem đã có data đấu giá chưa, chưa có thì bơm mồi
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

    // Hàm cực kỳ quan trọng để lấp đầy dữ liệu mẫu theo đúng Rubric
    private void seedIfMissing(AppDataStore targetStore) {
        System.out.println("TV3: Đang bơm toàn bộ dữ liệu mẫu (User, Item, Auction)...");

        // 1. Khởi tạo 1 Bidder và 1 Seller
        Bidder bidder1 = new Bidder("U001", "bidder01", "123456");
        Seller seller1 = new Seller("U002", "seller01", "123456");
        targetStore.getUsers().add(bidder1);
        targetStore.getUsers().add(seller1);

        // 2. Khởi tạo 2-3 Item bằng các class kế thừa
        ElectronicsItem item1 = new ElectronicsItem("I001", seller1.getId(), "Laptop Macbook Pro", "Máy mới 99%", 20000000L);
        VehicleItem item2 = new VehicleItem("I002", seller1.getId(), "Xe máy Honda SH", "Xe chính chủ, màu đen", 50000000L);
        targetStore.getItems().add(item1);
        targetStore.getItems().add(item2);

        // 3. Khởi tạo 2-3 Phiên đấu giá liên kết chặt chẽ với Item và Seller vừa tạo
        Auction a1 = new Auction(
                "A001", item1.getId(), seller1.getId(), 20000000L, 500000L,
                AuctionStatus.RUNNING,
                LocalDateTime.now().plusDays(2)
        );

        Auction a2 = new Auction(
                "A002", item2.getId(), seller1.getId(), 50000000L, 1000000L,
                AuctionStatus.OPEN,
                LocalDateTime.now().plusDays(5)
        );

        targetStore.getAuctions().add(a1);
        targetStore.getAuctions().add(a2);

        // Ép lưu xuống ổ cứng
        save(targetStore);
    }

    public AppDataStore getStore() {
        return this.store;
    }
}