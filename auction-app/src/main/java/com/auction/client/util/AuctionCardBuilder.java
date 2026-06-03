package com.auction.client.util;

import java.util.function.Consumer;

import com.auction.client.context.ClientSession;
import com.auction.shared.factory.UserFactory;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;
import com.auction.shared.model.item.ItemType;
import com.auction.shared.model.user.Role;
import com.auction.shared.model.user.User;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Tạo card phiên đấu giá dùng chung cho mọi màn hình danh sách
 * (AuctionList, BidderDashboard, SellerAuctions, MyAuctions...).
 *
 * Nút hành động ở đáy card được phân theo role:
 *   - BIDDER + phiên RUNNING/OPEN → "Vào đấu giá"  (btn-primary)
 *   - SELLER hoặc ADMIN           → "Xem chi tiết" (btn-outline)
 *   - Phiên đã kết thúc/thanh toán/hủy (mọi role) → "Xem chi tiết" (btn-outline)
 */
public final class AuctionCardBuilder {

    private AuctionCardBuilder() {}

    /**
     * @param auction    dữ liệu phiên đấu giá
     * @param onAction   callback khi bấm nút — nhận Auction tương ứng
     */
    public static VBox build(Auction auction, Consumer<Auction> onAction) {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(20));
        card.setPrefWidth(300);
        card.setMaxWidth(340);

        // ── Hàng trên: icon loại sản phẩm + badge trạng thái ──────────────
        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label typeIcon = new Label(itemTypeIcon(auction.getItemType()));
        typeIcon.getStyleClass().add("card-item-icon");
        typeIcon.setStyle("-fx-font-size: 22px; -fx-text-fill: -app-text-muted;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusBadge = new Label(EnumFormatter.auctionStatusVi(auction.getStatus()));
        statusBadge.getStyleClass().addAll("badge", EnumFormatter.auctionStatusBadgeClass(auction.getStatus()));

        topRow.getChildren().addAll(typeIcon, spacer, statusBadge);

        // ── Tên sản phẩm ──────────────────────────────────────────────────
        String title = (auction.getItemName() != null && !auction.getItemName().isBlank())
                ? auction.getItemName()
                : "Mã phiên " + shortId(auction.getId());
        Label nameLabel = new Label(title);
        nameLabel.getStyleClass().add("title-medium");
        nameLabel.setWrapText(true);

        // ── Mã phiên ──────────────────────────────────────────────────────
        Label codeLabel = new Label("Mã phiên: " + shortId(auction.getId()));
        codeLabel.getStyleClass().add("label-caption");

        // ── Separator region ──────────────────────────────────────────────
        Region divider = new Region();
        divider.setStyle("-fx-background-color: -app-border; -fx-pref-height: 1px; -fx-max-height: 1px;");

        // ── Giá hiện tại hoặc giá khởi điểm ──────────────────────────────
        boolean isEnded = auction.getStatus() == AuctionStatus.FINISHED
                || auction.getStatus() == AuctionStatus.PAID
                || auction.getStatus() == AuctionStatus.CANCELED;

        String priceCaption = isEnded ? "GIÁ CHỐT" : "GIÁ HIỆN TẠI";
        if (auction.getStatus() == AuctionStatus.OPEN
                && (auction.getBidHistory() == null || auction.getBidHistory().isEmpty())) {
            priceCaption = "GIÁ KHỞI ĐIỂM";
        }

        Label priceCaptionLabel = new Label(priceCaption);
        priceCaptionLabel.getStyleClass().add("label-caption");

        Label priceLabel = new Label(MoneyFormatter.formatVnd(auction.getCurrentPrice()));
        priceLabel.getStyleClass().add("price-medium");

        // ── Thông tin phụ: winner / step / countdown ───────────────────────
        Label subInfoLabel = buildSubInfo(auction);

        // ── Nút hành động ─────────────────────────────────────────────────
        Button actionButton = buildActionButton(auction, onAction);

        card.getChildren().addAll(
                topRow,
                nameLabel,
                codeLabel,
                divider,
                priceCaptionLabel,
                priceLabel,
                subInfoLabel,
                actionButton
        );

        return card;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Nút hành động phân theo role + trạng thái phiên:
     *
     * - Phiên đã kết thúc / hủy (mọi role)     → "Xem chi tiết" (btn-outline)
     * - BIDDER + RUNNING/OPEN                   → "Vào đấu giá"  (btn-primary)
     * - SELLER hoặc ADMIN (mọi trạng thái)      → "Xem chi tiết" (btn-outline)
     */
    private static Button buildActionButton(Auction auction, Consumer<Auction> onAction) {
        boolean isActive = auction.getStatus() == AuctionStatus.RUNNING
                || auction.getStatus() == AuctionStatus.OPEN;

        boolean isBidder = isBidderSession();

        String btnText;
        String btnStyle;

        if (isActive && isBidder) {
            btnText  = "Vào đấu giá";
            btnStyle = "btn-primary";
        } else {
            btnText  = "Xem chi tiết";
            btnStyle = "btn-outline";
        }

        Button btn = new Button(btnText);
        btn.getStyleClass().add(btnStyle);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> {
            if (onAction != null) onAction.accept(auction);
        });
        return btn;
    }

    /**
     * Label thông tin phụ dưới giá:
     * - RUNNING → countdown + bước giá
     * - OPEN    → thời gian mở + bước giá
     * - FINISHED/PAID → người thắng
     * - CANCELED → "Không có người thắng"
     */
    private static Label buildSubInfo(Auction auction) {
        String text;
        switch (auction.getStatus()) {
            case RUNNING -> {
                String countdown = formatCountdown(auction);
                String step = auction.getMinIncrement() > 0
                        ? "  +%s/bước".formatted(MoneyFormatter.formatVnd(auction.getMinIncrement()))
                        : "";
                text = "⏱  " + countdown + step;
            }
            case OPEN -> {
                String openTime = auction.getStartTime() != null
                        ? "Mở sau " + formatCountdown(auction)
                        : "Sắp mở";
                String step = auction.getMinIncrement() > 0
                        ? "  +%s/bước".formatted(MoneyFormatter.formatVnd(auction.getMinIncrement()))
                        : "";
                text = "⏱  " + openTime + step;
            }
            case FINISHED, PAID -> {
                // Server enrich highestBidderName; fallback về winnerBidderId nếu chưa có tên
                String winner = auction.getHighestBidderName() != null && !auction.getHighestBidderName().isBlank()
                        ? auction.getHighestBidderName()
                        : auction.getWinnerBidderId();
                text = winner != null ? "Người thắng: " + winner : "Không có người thắng";
            }
            case CANCELED -> text = "Không có người thắng";
            default -> text = "";
        }

        Label label = new Label(text);
        label.getStyleClass().add("text-secondary");
        label.setWrapText(true);
        return label;
    }

    /** Trả về chuỗi countdown còn lại (HH:mm:ss hoặc "Đã kết thúc"). */
    private static String formatCountdown(Auction auction) {
        java.time.LocalDateTime target;
        if (auction.getStatus() == AuctionStatus.OPEN) {
            target = auction.getStartTime();
        } else {
            target = auction.getEndTime();
        }
        if (target == null) return "--:--:--";
        java.time.Duration remaining = java.time.Duration.between(
                java.time.LocalDateTime.now(), target);
        if (remaining.isNegative() || remaining.isZero()) return "Đang kết thúc...";
        return CountdownUtil.formatRemaining(remaining);
    }

    /**
     * Icon emoji theo loại sản phẩm.
     * Auction.getItemType() có thể null nếu server chưa enrich → fallback "📦".
     */
    private static String itemTypeIcon(ItemType type) {
        if (type == null) return "📦";
        return switch (type) {
            case ELECTRONICS -> "📱";
            case VEHICLE     -> "🚗";
            case ART         -> "🎨";
        };
    }

    /** Lấy 6 ký tự đầu của ID, uppercase. */
    private static String shortId(String id) {
        if (id == null) return "---";
        String s = id.replace("-", "");
        return (s.length() > 6 ? s.substring(0, 6) : s).toUpperCase();
    }

    /** Kiểm tra session hiện tại có phải BIDDER không (an toàn khi null). */
    private static boolean isBidderSession() {
        User user = ClientSession.getCurrentUser();
        if (user == null) return false;
        try {
            return UserFactory.toRole(user) == Role.BIDDER;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}