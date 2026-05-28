package com.auction.server.service;

import com.auction.shared.model.auction.Auction;

public interface AuctionLifecycleService {

    /**
     * Đồng bộ trạng thái auction theo thời gian hiện tại.
     * - OPEN  + đã quá startTime → start()
     * - RUNNING + đã quá endTime → finish()
     * - Terminal states (FINISHED/PAID/CANCELED) → không đổi
     *
     * Idempotent: gọi nhiều lần kết quả như 1 lần.
     * Nếu có transition, sẽ broadcast AuctionUpdateEvent cho client.
     *
     * @return Auction sau khi sync (caller có thể dùng luôn, không phải load lại)
     */
    Auction syncByTime(String auctionId);

    /**
     * Finish thủ công (vd: admin can thiệp). Service sẽ validate auction
     * đang RUNNING và broadcast event sau khi finish.
     */
    Auction finishAuction(String auctionId);

    /**
     * Cancel thủ công (vd: admin hủy phiên).
     */
    Auction cancelAuction(String auctionId);
    Auction markAuctionPaid(String auctionId);


    // ── Eager scheduling (THÊM MỚI) ───────────────────────
    /**
     * Schedule task tự động chạy auction.start() khi tới startTime.
     * Gọi ngay sau khi tạo auction OPEN.
     */
    void scheduleStart(Auction auction);

    /**
     * Schedule task tự động chạy auction.finish() khi tới endTime.
     * Gọi ngay sau khi tạo auction (hoặc sau khi start).
     */
    void scheduleClose(Auction auction);

    /**
     * Hủy task close cũ và schedule lại với endTime mới.
     * Dùng khi anti-sniping gia hạn endTime.
     */
    void rescheduleClose(Auction auction);
    /**
     * Schedule task tự động cancel auction nếu winner không thanh toán
     * trong vòng PAYMENT_WINDOW_HOURS sau khi FINISHED.
     */
    void schedulePaymentTimeout(Auction auction);

    /**
     * Cleanup khi server shutdown — cancel tất cả scheduled tasks.
     */
    void shutdown();
}