package com.auction.server.service;

import com.auction.shared.model.Auction;
import java.time.Duration;
import java.time.LocalDateTime;

public interface AntiSnipingService {

    /**
     * Predicate thuần: kiểm tra có cần gia hạn không (KHÔNG sửa auction).
     * Tách ra để test dễ và để caller có thể decide không apply.
     */
    boolean shouldExtend(Auction auction, LocalDateTime now);

    /**
     * Apply gia hạn lên auction (mutate). Chỉ gọi sau khi shouldExtend == true.
     * KHÔNG tự reschedule scheduler — caller (BidService) phối hợp với LifecycleService.
     *
     * @return số giây đã gia hạn (để caller log/broadcast)
     */
    long applyExtension(Auction auction);

    /** Cho phép caller đọc config (vd: để log "đã gia hạn 60s"). */
    Duration getExtensionDuration();
    Duration getTriggerWindow();
}