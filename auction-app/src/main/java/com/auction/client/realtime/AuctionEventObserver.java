package com.auction.client.realtime;

import com.auction.shared.networkMessage.event.AuctionEvent;

/**
 * Interface cho các component muốn theo dõi cập nhật phiên đấu giá
 * 
 * Cách dùng:
 * 1. Controller implement interface này
 * 2. Gọi AuctionEventBus.getInstance().addObserver(this) trong initialize()
 * 3. Triển khai onAuctionUpdated() để cập nhật UI
 * 4. Gọi AuctionEventBus.getInstance().removeObserver(this) trong dispose()
 */
public interface AuctionEventObserver {
    void onAuctionUpdated(AuctionEvent event);
}