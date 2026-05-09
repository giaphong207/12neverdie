package com.auction.server.dao;

import org.junit.jupiter.api.Test;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

class DataManagerTest {

    @Test
    void shouldSaveAndLoadDataStore() {
        // 1. Khởi tạo DataManager (Hệ thống sẽ tự động gọi load() và seedIfMissing() để bơm dữ liệu)
        DataManager manager = DataManager.getInstance();

        // 2. Kiểm tra xem trên RAM đã có dữ liệu mẫu chưa
        assertNotNull(manager.getStore(), "Kho dữ liệu không được phép null");
        assertFalse(manager.getStore().getUsers().isEmpty(), "Phải có User mẫu (Bidder, Seller)");
        assertFalse(manager.getStore().getItems().isEmpty(), "Phải có Item mẫu (Electronics, Vehicle)");
        assertFalse(manager.getStore().getAuctions().isEmpty(), "Phải có Auction mẫu");

        // 3. Ép lưu xuống ổ cứng để tạo file
        manager.save(manager.getStore());

        // 4. Ra ổ cứng tìm xem file database.dat có thực sự tồn tại không
        File file = new File("data/database.dat");
        assertTrue(file.exists(), "Lỗi: File database.dat chưa được tạo ra trong thư mục data/");

        // 5. Thử mở file đọc ngược lên lại xem cấu trúc byte có bị hỏng không
        assertDoesNotThrow(() -> {
            manager.load();
        }, "Lỗi: File database.dat đã lưu nhưng không thể đọc lại được (Deserialization failed)");

        System.out.println("PASS: Toàn bộ dữ liệu mẫu đã được tạo, lưu và đọc lại thành công 100%!");
    }
}