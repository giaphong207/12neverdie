package com.auction.server.service;

import com.auction.shared.config.AppConfig;
import com.auction.shared.model.Auction;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Triển khai anti-sniping: tự động gia hạn phiên khi có bid ở giây cuối.
 *
 * Cách hoạt động từng bước:
 *   1. Kiểm tra auction có đang RUNNING không → nếu không thì bỏ qua
 *   2. Tính số giây còn lại = endTime - triggerTime
 *   3. Nếu còn <= 30 giây → gọi auction.extendEndTime(60) → return true
 *   4. Nếu còn > 30 giây → không làm gì → return false
 */
public class DefaultAntiSnipingService implements AntiSnipingService {

    @Override
    public boolean applyExtensionIfNeeded(Auction auction, LocalDateTime triggerTime) {

        // Bước 1: Chỉ áp dụng khi phiên đang chạy
        if (!auction.isRunning()) {
            return false;
        }

        // Bước 2: Tính số giây còn lại
        // Duration.between(from, to) → khoảng thời gian từ triggerTime đến endTime
        long remainingSeconds = Duration
                .between(triggerTime, auction.getEndTime())
                .getSeconds();

        // Bước 3: Nếu đang ở trong cửa sổ anti-sniping (≤ 30 giây)
        if (remainingSeconds <= AppConfig.ANTI_SNIPING_TRIGGER_SECONDS) {
            auction.extendEndTime(AppConfig.ANTI_SNIPING_EXTENSION_SECONDS);
            System.out.println("[AntiSniping] Phiên " + auction.getId()
                    + " được gia hạn thêm " + AppConfig.ANTI_SNIPING_EXTENSION_SECONDS
                    + "s. EndTime mới: " + auction.getEndTime());
            return true;
        }

        // Bước 4: Còn nhiều thời gian → không cần gia hạn
        return false;
    }
}