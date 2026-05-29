package com.auction.client.util;

/**
 * Hợp đồng cho các controller có resource cần dọn dẹp khi scene của nó bị thay.
 *
 * Resource thường gặp:
 *   - Observer đã đăng ký vào AuctionEventBus
 *   - Timeline đang chạy (countdown, polling, ...)
 *   - Binding đã gắn vào property sống lâu (ClientSession.balanceProperty)
 *
 * Cách dùng:
 *   1. Controller implements Disposable
 *   2. Viết logic dọn dẹp vào method dispose()
 *   3. SceneNavigator.switchScene() sẽ tự gọi dispose() của controller cũ
 *      ngay trước khi load scene mới — bạn không cần gọi tay
 *
 * Nếu controller có handleLogout(), nên cho handleLogout() gọi switchScene
 * như bình thường — dispose() vẫn được trigger tự động.
 */
public interface Disposable {
    void dispose();
}