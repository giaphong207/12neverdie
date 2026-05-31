package com.auction.client.util;

import java.util.Optional;
import java.util.function.Consumer;

import com.auction.client.context.ClientSession;
import com.auction.client.util.SidebarBuilder.NavKey;
import com.auction.shared.factory.UserFactory;
import com.auction.shared.model.user.Role;
import com.auction.shared.model.user.User;
import com.auction.shared.networkMessage.Requests.DepositRequest;
import com.auction.shared.networkMessage.Results.DepositResult;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
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
 * Layout: [Logo · Portal] [Nav 1] ... [Nav N] [spacer] [Ví · Nạp tiền] [Avatar]
 * Nav có mục "Hướng dẫn" mở cửa sổ thông tin; bấm avatar hiện popup "Đăng xuất".
 */
public final class TopbarBuilder {

    private TopbarBuilder() {}

    public static HBox build(User user, NavKey activeKey,
                             Consumer<NavKey> onNavClick,
                             Runnable onLogout) {
        HBox topbar = new HBox();
        topbar.getStyleClass().add("topbar");
        topbar.setAlignment(Pos.CENTER_LEFT);

        HBox brand = new HBox(12);
        brand.setAlignment(Pos.CENTER_LEFT);
        Label logo = new Label("Phiên Đấu Giá");
        logo.getStyleClass().add("topbar-logo");
        Region brandDivider = new Region();
        brandDivider.getStyleClass().add("topbar-divider");
        Label portalLabel = new Label(portalLabelFor(user));
        portalLabel.getStyleClass().add("topbar-portal-label");
        brand.getChildren().addAll(logo, brandDivider, portalLabel);

        Region brandGap = new Region();
        brandGap.setPrefWidth(32);

        HBox navBox = new HBox(4);
        navBox.setAlignment(Pos.CENTER_LEFT);
        navBox.setMinWidth(Region.USE_PREF_SIZE);   // cả dải nav không bị bóp nhỏ
        for (NavItem item : navItemsFor(user)) {
            Button btn = new Button(item.label);
            btn.getStyleClass().add("topbar-nav-item");
            if (item.key == activeKey) {
                btn.getStyleClass().add("topbar-nav-item-active");
            }
            // luôn giữ đủ bề rộng theo chữ → không bị cắt thành "Trang ch..."
            btn.setMinWidth(Region.USE_PREF_SIZE);
            btn.setWrapText(false);
            btn.setOnAction(e -> {
                // "Hướng dẫn" điều hướng tới trang Help ngay trong cửa sổ
                // (không cần controller xử lý → tránh popup "đang phát triển").
                if (isHelpKey(item.key)) {
                    SceneNavigator.switchScene("/fxml/Help.fxml");
                } else if (onNavClick != null) {
                    onNavClick.accept(item.key);
                }
            });
            navBox.getChildren().add(btn);
        }

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);

        // ── Khối bên phải: [ví + nạp tiền] (ẩn với admin) → avatar ──
        HBox rightBox = new HBox(14);
        rightBox.setAlignment(Pos.CENTER_RIGHT);

        boolean isAdmin = user != null && UserFactory.toRole(user) == Role.ADMIN;
        if (!isAdmin) {
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

            Button depositBtn = new Button("+ Nạp tiền");
            depositBtn.getStyleClass().add("topbar-deposit");
            depositBtn.setOnAction(e -> openDepositDialog());

            rightBox.getChildren().addAll(walletBox, depositBtn);
        }

        // ── Avatar tròn chữ tắt (b1 / s1 / A) màu theo vai trò ──
        // Bấm vào avatar → popup nhỏ gọn chỉ có "Đăng xuất".
        Label avatar = new Label(initialsFor(user));
        avatar.getStyleClass().addAll("avatar-circle", avatarClassFor(user));

        ContextMenu accountMenu = buildAccountMenu(onLogout);
        avatar.setOnMouseClicked(e ->
                accountMenu.show(avatar, Side.BOTTOM, 0, 8));

        rightBox.getChildren().add(avatar);

        topbar.getChildren().addAll(brand, brandGap, navBox, grow, rightBox);
        return topbar;
    }

    /** Các nav key "Hướng dẫn" (trước đây là Cài đặt/Cấu hình) — mở cửa sổ thông tin. */
    private static boolean isHelpKey(NavKey key) {
        return key == NavKey.BIDDER_SETTINGS
                || key == NavKey.SELLER_SETTINGS
                || key == NavKey.ADMIN_SETTINGS;
    }

    /** Popup nhỏ gọn (dropdown) dưới avatar, chỉ có một mục "Đăng xuất". */
    private static ContextMenu buildAccountMenu(Runnable onLogout) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("account-menu");

        Label logout = new Label("Đăng xuất");
        logout.getStyleClass().add("account-menu-item");
        logout.setMaxWidth(Double.MAX_VALUE);

        CustomMenuItem item = new CustomMenuItem(logout, true);
        item.setOnAction(e -> {
            if (onLogout != null) onLogout.run();
        });
        menu.getItems().add(item);
        return menu;
    }

    /** Chữ tắt cho avatar: chữ cái đầu + cụm số cuối nếu có (bidder1→b1, admin→A). */
    private static String initialsFor(User user) {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            return "?";
        }
        String name = user.getUsername().trim();
        char first = name.charAt(0);
        // lấy cụm chữ số ở cuối tên (nếu có)
        int i = name.length();
        while (i > 0 && Character.isDigit(name.charAt(i - 1))) i--;
        String trailingDigits = name.substring(i);
        if (!trailingDigits.isEmpty()) {
            return Character.toLowerCase(first) + trailingDigits;
        }
        // không có số → in hoa chữ cái đầu
        return String.valueOf(Character.toUpperCase(first));
    }

    /** Class màu avatar theo vai trò. */
    private static String avatarClassFor(User user) {
        if (user == null) return "avatar-bidder";
        return switch (UserFactory.toRole(user)) {
            case BIDDER -> "avatar-bidder";
            case SELLER -> "avatar-seller";
            case ADMIN -> "avatar-admin";
        };
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

    /** Nav key "Hướng dẫn" tương ứng vai trò — để HelpController highlight đúng mục. */
    public static NavKey helpKeyFor(User user) {
        if (user == null) return NavKey.BIDDER_SETTINGS;
        return switch (UserFactory.toRole(user)) {
            case BIDDER -> NavKey.BIDDER_SETTINGS;
            case SELLER -> NavKey.SELLER_SETTINGS;
            case ADMIN -> NavKey.ADMIN_SETTINGS;
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
                    new NavItem(NavKey.BIDDER_SETTINGS, "Hướng dẫn")
            );
            case SELLER -> java.util.List.of(
                    new NavItem(NavKey.SELLER_OVERVIEW, "Tổng quan"),
                    new NavItem(NavKey.SELLER_PRODUCTS, "Sản phẩm của tôi"),
                    new NavItem(NavKey.SELLER_AUCTIONS, "Phiên đấu giá"),
                    new NavItem(NavKey.SELLER_SETTINGS, "Hướng dẫn")
            );
            case ADMIN -> java.util.List.of(
                    new NavItem(NavKey.ADMIN_OVERVIEW, "Tổng quan"),
                    new NavItem(NavKey.ADMIN_USERS, "Người dùng"),
                    new NavItem(NavKey.ADMIN_AUCTIONS, "Phiên đấu giá"),
                    new NavItem(NavKey.ADMIN_REPORTS, "Báo cáo"),
                    new NavItem(NavKey.ADMIN_SETTINGS, "Hướng dẫn")
            );
        };
    }

    private record NavItem(NavKey key, String label) {}
}
