package com.auction;

import com.auction.server.dao.AppDataStore;
import com.auction.server.dao.DataManager;
import org.junit.jupiter.api.Test;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

class DataManagerTest {

    @Test
    void shouldSaveAndLoadDataStore() {
        System.out.println("--- BẮT ĐẦU TEST ---");

        // 1. Gọi thủ kho lấy dữ liệu
        DataManager manager = DataManager.getInstance();
        AppDataStore store = manager.getStore();

        // Đảm bảo kho không bị rỗng (null)
        assertNotNull(store.getUsers(), "Danh sách User không được null");

        // 2. Ra lệnh lưu xuống ổ cứng
        manager.save(store);

        // 3. Bắt đầu đi tìm xem có file database.dat thật không
        File file = new File("data/database.dat");
        assertTrue(file.exists(), "Lỗi: File database.dat chưa được tạo ra!");

        // 4. Thử đọc ngược file đó lên lại xem có lỗi không
        AppDataStore loadedStore = manager.load();
        assertNotNull(loadedStore, "Lỗi: Không đọc được file lên!");

        System.out.println("--- TEST THÀNH CÔNG RỰC RỠ ---");
    }
}