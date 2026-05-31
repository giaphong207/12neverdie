package com.auction.client.util;

import com.auction.client.util.SidebarBuilder.NavKey;

/**
 * Bộ định tuyến nav dùng chung cho mọi trang.
 *
 * Trước đây mỗi controller tự xử lý một phần NavKey nên bấm sang trang
 * mà controller hiện tại "không biết" sẽ rơi vào nhánh "đang phát triển".
 * Tập trung ánh xạ tại đây để từ BẤT KỲ trang nào cũng đi được tới mọi mục.
 */
public final class NavRouter {

    private NavRouter() {}

    /** Điều hướng theo mục nav. Mục chưa có trang thật sẽ báo "Sắp ra mắt". */
    public static void route(NavKey key) {
        if (key == null) return;
        switch (key) {
            // ===== Bidder =====
            case BIDDER_HOME -> SceneNavigator.switchScene("/fxml/BidderDashboard.fxml");
            case BIDDER_LIVE -> SceneNavigator.switchScene("/fxml/AuctionList.fxml");
            case BIDDER_MINE -> SceneNavigator.switchScene("/fxml/MyAuctions.fxml");
            case BIDDER_WON -> SceneNavigator.switchScene("/fxml/WonAuctions.fxml");
            case BIDDER_SETTINGS -> SceneNavigator.switchScene("/fxml/Help.fxml");

            // ===== Seller =====
            case SELLER_OVERVIEW -> SceneNavigator.switchScene("/fxml/SellerDashboard.fxml");
            case SELLER_PRODUCTS -> SceneNavigator.switchScene("/fxml/ProductManagement.fxml");
            case SELLER_AUCTIONS -> SceneNavigator.switchScene("/fxml/SellerAuctions.fxml");
            case SELLER_SETTINGS -> SceneNavigator.switchScene("/fxml/Help.fxml");

            // ===== Admin =====
            case ADMIN_OVERVIEW -> SceneNavigator.switchScene("/fxml/AdminDashboard.fxml");
            case ADMIN_USERS -> SceneNavigator.switchScene("/fxml/AdminUsers.fxml");
            case ADMIN_AUCTIONS -> SceneNavigator.switchScene("/fxml/AuctionList.fxml");
            case ADMIN_SETTINGS -> SceneNavigator.switchScene("/fxml/Help.fxml");

            // Các mục chưa có trang riêng
            default -> AlertUtils.showInfo("Sắp ra mắt", "Tính năng này đang được phát triển.");
        }
    }
}
