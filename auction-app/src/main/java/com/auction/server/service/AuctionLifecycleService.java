package com.auction.server.service;

import com.auction.shared.model.Auction;
import java.util.List;

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
     * Sync tất cả auction. Dùng cho cron job định kỳ làm safety net.
     */
    void syncAll();

    /**
     * Finish thủ công (vd: admin can thiệp). Service sẽ validate auction
     * đang RUNNING và broadcast event sau khi finish.
     */
    Auction finishAuction(String auctionId);

    /**
     * Cancel thủ công (vd: admin hủy phiên).
     */
    Auction cancelAuction(String auctionId);
}