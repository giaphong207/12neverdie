package com.auction.client.util;

import java.util.Optional;
import java.util.function.Consumer;

import com.auction.client.context.ClientSession;
import com.auction.client.util.SidebarBuilder.NavKey;
import com.auction.shared.factory.UserFactory;
import com.auction.shared.model.user.User;
import com.auction.shared.networkMessage.Requests.DepositRequest;
import com.auction.shared.networkMessage.Results.DepositResult;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Topbar ngang đặt ở trên cùng — thay thế sidebar bên trái.
 * Tái sử dụng {@link SidebarBuilder.NavKey} để controller không phải đổi logic.
 *
 * Layout: [Logo · Portal] [Nav 1] [Nav 2] ... [Nav N] [spacer] [User · Role] [Đăng xuất]
 */
public final class TopbarBuilder {

    private TopbarBuilder() {}

    public static HBox build(User user, NavKey activeKey,
                             Consumer<NavKey> onNavClick,
                             Runnable onLogout) {
        HBox topbar = new HBox();
        topbar.getStyleClass().add("topbar");
        topbar.setAlignment(Pos.CENTER_LEFT);

        VBox brand = new VBox(2);
        brand.setAlignment(Pos.CENTER_LEFT);
        Label logo = new Label("Phiên Đấu Giá");
        logo.getStyleClass().add("topbar-logo");
        Label portalLabel = new Label(portalLabelFor(user));
        portalLabel.getStyleClass().add("topbar-portal-label");
        brand.getChildren().addAll(logo, portalLabel);

        Region brandGap = new Region();
        brandGap.setPrefWidth(32);

        HBox navBox = new HBox(4);
        navBox.setAlignment(Pos.CENTER_LEFT);
        for (NavItem item : navItemsFor(user)) {
            Button btn = new Button(item.label);
            btn.getStyleClass().add("topbar-nav-item");
            if (item.key == activeKey) {
                btn.getStyleClass().add("topbar-nav-item-active");
            }
            btn.setOnAction(e -> {
                if (onNavClick != null) onNavClick.accept(item.key);
            });
            navBox.getChildren().add(btn);
        }

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);

        // ── VÍ ──
        VBox walletBox = new VBox(0);
        walletBox.setAlignment(Pos.CENTER_RIGHT);
        Label walletCaption = new Label("VÍ CỦA TÔI");
        walletCaption.getStyleClass().add("topbar-role");
        Label walletAmountLabel = new Label();
        walletAmountLabel.getStyleClass().add("topbar-wallet");

// Hiển thị số dư hiện tại + cập nhật khi balance đổi,
// nhưng KHÔNG để balanceProperty (tĩnh, sống mãi) giữ chặt label này.
        Runnable refreshWallet = () -> walletAmountLabel.setText(
                MoneyFormatter.formatVnd(ClientSession.balanceProperty().get()));
        refreshWallet.run(); // set giá trị ban đầu ngay

        InvalidationListener walletListener = obs -> refreshWallet.run();
        walletAmountLabel.getProperties().put("walletBalanceListener", walletListener); // giữ listener sống bằng tuổi label
        ClientSession.balanceProperty().addListener(new WeakInvalidationListener(walletListener));
        walletBox.getChildren().addAll(walletCaption, walletAmountLabel);

        Button depositBtn = new Button("Nạp tiền");
        depositBtn.getStyleClass().add("topbar-deposit");
        depositBtn.setOnAction(e -> openDepositDialog());

        VBox userBox = new VBox(0);
        userBox.setAlignment(Pos.CENTER_RIGHT);
        Label usernameLabel = new Label(user != null ? user.getUsername() : "Người dùng");
        usernameLabel.getStyleClass().add("topbar-username");
        Label roleLabel = new Label(user != null ? EnumFormatter.roleVi(UserFactory.toRole(user)) : "");
        roleLabel.getStyleClass().add("topbar-role");
        userBox.getChildren().addAll(usernameLabel, roleLabel);

        Button logoutBtn = new Button("Đăng xuất");
        logoutBtn.getStyleClass().add("topbar-logout");
        logoutBtn.setOnAction(e -> {
            if (onLogout != null) onLogout.run();
        });

        topbar.getChildren().addAll(brand, brandGap, navBox, grow,
                walletBox, depositBtn, userBox, logoutBtn);
        return topbar;
    }

    /** Mở dialog nhập số tiền nạp, gửi DepositRequest, update ClientSession balance khi server trả về. */
    private static void openDepositDialog() {
        User user = ClientSession.getCurrentUser();
        if (user == null) {
            AlertUtils.showWarning("Chưa đăng nhập", "Bạn cần đăng nhập trước khi nạp tiền.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nạp tiền vào ví");
        dialog.setHeaderText("Số dư hiện tại: " + MoneyFormatter.formatVnd(ClientSession.getBalance()));
        dialog.setContentText("Số tiền nạp (VND):");

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) return;

        long amount;
        try {
            amount = Long.parseLong(result.get().trim());
        } catch (NumberFormatException ex) {
            AlertUtils.showError("Lỗi", "Số tiền không hợp lệ");
            return;
        }
        if (amount <= 0) {
            AlertUtils.showWarning("Lỗi", "Số tiền phải > 0");
            return;
        }

        RequestExecutor.send(
                new DepositRequest(user.getId(), amount),
                response -> {
                    if (response instanceof DepositResult.Success ok) {
                        ClientSession.setBalance(ok.newBalance());
                        AlertUtils.showInfo("Thành công",
                                "Đã nạp " + MoneyFormatter.formatVnd(amount)
                                        + ". Số dư mới: " + MoneyFormatter.formatVnd(ok.newBalance()));
                    } else if (response instanceof DepositResult.Failure f) {
                        AlertUtils.showError("Nạp thất bại", f.reason());
                    }
                },
                error -> AlertUtils.showError("Lỗi mạng", "Không gửi được yêu cầu: " + error)
        );
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
                    new NavItem(NavKey.ADMIN_OVERVIEW, "Tổng quan"),
                    new NavItem(NavKey.ADMIN_USERS, "Người dùng"),
                    new NavItem(NavKey.ADMIN_AUCTIONS, "Phiên đấu giá"),
                    new NavItem(NavKey.ADMIN_REPORTS, "Báo cáo"),
                    new NavItem(NavKey.ADMIN_SETTINGS, "Cấu hình")
            );
        };
    }

    private record NavItem(NavKey key, String label) {}
}
