package com.auction.client.util;

import java.util.function.Consumer;

import com.auction.shared.factory.UserFactory;
import com.auction.shared.model.user.User;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Tạo sidebar dùng chung cho cả 3 role (Bidder / Seller / Admin).
 * Cấu trúc: Logo + Portal label + 5 nav items + spacer + user info + logout.
 */
public final class SidebarBuilder {

    public enum NavKey {
        // Bidder
        BIDDER_HOME, BIDDER_LIVE, BIDDER_MINE, BIDDER_WON, BIDDER_SETTINGS,
        // Seller
        SELLER_OVERVIEW, SELLER_PRODUCTS, SELLER_AUCTIONS, SELLER_REVENUE, SELLER_SETTINGS,
        // Admin
        ADMIN_OVERVIEW, ADMIN_USERS, ADMIN_AUCTIONS, ADMIN_REPORTS, ADMIN_SETTINGS
    }

    private SidebarBuilder() {}

    /**
     * Tạo sidebar VBox đầy đủ cho user hiện tại.
     *
     * @param user        User hiện tại (lấy role để chọn nav)
     * @param activeKey   mục đang được highlight
     * @param onNavClick  callback khi click vào nav item
     * @param onLogout    callback khi click logout
     */
    public static VBox build(User user, NavKey activeKey,
                             Consumer<NavKey> onNavClick,
                             Runnable onLogout) {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");

        // 1. Logo block
        VBox logoBlock = new VBox(4);
        logoBlock.setPadding(new Insets(0, 0, 0, 32));
        Label logo = new Label("Phiên Đấu Giá");
        logo.getStyleClass().add("sidebar-logo");
        Label portalLabel = new Label(portalLabelFor(user));
        portalLabel.getStyleClass().add("sidebar-portal-label");
        logoBlock.getChildren().addAll(logo, portalLabel);

        // 2. Spacer trước nav
        Region spacer1 = new Region();
        spacer1.setPrefHeight(40);

        // 3. Nav items
        VBox navBox = new VBox(2);
        for (NavItem item : navItemsFor(user)) {
            Button btn = new Button(item.label);
            btn.getStyleClass().add("sidebar-nav-item");
            if (item.key == activeKey) {
                btn.getStyleClass().add("sidebar-nav-item-active");
            }
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setOnAction(e -> {
                if (onNavClick != null) onNavClick.accept(item.key);
            });
            navBox.getChildren().add(btn);
        }

        // 4. Spacer đẩy user info xuống đáy
        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);

        // 5. User info + logout
        VBox userBox = new VBox(4);
        userBox.setPadding(new Insets(16, 32, 0, 32));
        Label usernameLabel = new Label(user != null ? user.getUsername() : "Người dùng");
        usernameLabel.getStyleClass().add("sidebar-username");
        Label roleLabel = new Label(user != null ? EnumFormatter.roleVi(UserFactory.toRole(user)) : "");
        roleLabel.getStyleClass().add("sidebar-role");

        Button logoutBtn = new Button("Đăng xuất →");
        logoutBtn.getStyleClass().add("sidebar-logout");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setAlignment(Pos.CENTER_LEFT);
        logoutBtn.setPadding(new Insets(8, 0, 0, 0));
        logoutBtn.setOnAction(e -> {
            if (onLogout != null) onLogout.run();
        });

        userBox.getChildren().addAll(usernameLabel, roleLabel, logoutBtn);

        sidebar.getChildren().addAll(logoBlock, spacer1, navBox, grow, userBox);
        return sidebar;
    }

    private static String portalLabelFor(User user) {
        if (user == null) return "";
        return switch (UserFactory.toRole(user)) {
            case BIDDER -> "Người đấu giá";
            case SELLER -> "Người bán";
            case ADMIN -> "Quản trị viên";
        };
    }

    private static java.util.List<NavItem> navItemsFor(User user) {
        if (user == null) return java.util.List.of();
        return switch (UserFactory.toRole(user)) {
            case BIDDER -> java.util.List.of(
                    new NavItem(NavKey.BIDDER_HOME, "Trang chủ"),
                    new NavItem(NavKey.BIDDER_LIVE, "Phiên đang diễn ra"),
                    new NavItem(NavKey.BIDDER_MINE, "Phiên của tôi"),
                    new NavItem(NavKey.BIDDER_WON, "Đã thắng"),
                    new NavItem(NavKey.BIDDER_SETTINGS, "Cài đặt")
            );
            case SELLER -> java.util.List.of(
                    new NavItem(NavKey.SELLER_OVERVIEW, "Tổng quan"),
                    new NavItem(NavKey.SELLER_PRODUCTS, "Sản phẩm của tôi"),
                    new NavItem(NavKey.SELLER_AUCTIONS, "Phiên đấu giá"),
                    new NavItem(NavKey.SELLER_REVENUE, "Doanh thu"),
                    new NavItem(NavKey.SELLER_SETTINGS, "Cài đặt")
            );
            case ADMIN -> java.util.List.of(
                    new NavItem(NavKey.ADMIN_OVERVIEW, "Tổng quan hệ thống"),
                    new NavItem(NavKey.ADMIN_USERS, "Người dùng"),
                    new NavItem(NavKey.ADMIN_AUCTIONS, "Phiên đấu giá"),
                    new NavItem(NavKey.ADMIN_REPORTS, "Báo cáo"),
                    new NavItem(NavKey.ADMIN_SETTINGS, "Cấu hình hệ thống")
            );
        };
    }

    private record NavItem(NavKey key, String label) {}
}