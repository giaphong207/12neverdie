package com.auction.server.service;

import com.auction.shared.model.Auction;
import java.time.LocalDateTime;

/**
 * Interface định nghĩa quy tắc chống "sniping" (bid giây cuối để thắng dễ dàng).
 *
 * Quy tắc:
 *   - Nếu có bid hợp lệ khi phiên còn <= ANTI_SNIPING_TRIGGER_SECONDS giây
 *   - Thì tự động cộng thêm ANTI_SNIPING_EXTENSION_SECONDS giây vào endTime
 *   - Chỉ áp dụng 1 lần cho 1 manual bid, không áp dụng lặp cho auto-bid cascade
 */
public interface AntiSnipingService {

    /**
     * Kiểm tra và áp dụng gia hạn nếu cần.
     *
     * @param auction     phiên đang xử lý (sẽ bị chỉnh endTime trực tiếp nếu trigger)
     * @param triggerTime thời điểm bid xảy ra (thường là LocalDateTime.now())
     * @return true nếu đã gia hạn, false nếu không cần
     */
    boolean applyExtensionIfNeeded(Auction auction, LocalDateTime triggerTime);
}