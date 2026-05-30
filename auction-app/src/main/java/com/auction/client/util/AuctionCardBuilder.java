package com.auction.client.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Consumer;

import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Tạo card hiển thị 1 phiên đấu giá:
 * ┌─────────────────────────┐
 * │ [● ĐANG DIỄN RA badge ] │
 * │ ▒▒▒▒▒ ảnh placeholder ▒▒│ (chức năng ảnh thật chưa làm)
 * │ Tên sản phẩm            │
 * │ LOT №abc123             │
 * │ ─────────────           │
 * │ GIÁ HIỆN TẠI            │
 * │ 1.250.000.000 ₫         │
 * │ ⏱ 02:14:35              │
 * │ [ Xem chi tiết  ]       │
 * └─────────────────────────┘
 */
public final class AuctionCardBuilder {

    private AuctionCardBuilder() {}

    public static VBox build(Auction auction, Consumer<Auction> onViewDetail) {
        VBox card = new VBox(12);
        card.getStyleClass().add("auction-card");
        card.setPadding(new Insets(18));
        card.setPrefWidth(300);
        card.setMaxWidth(340);

        AuctionStatus status = auction.getStatus();

        // 1. Hàng đầu: icon chip (trái) + badge trạng thái (phải)
        //    (Auction chưa có ItemType nên dùng icon chung)
        Label iconChip = new Label("▣");
        iconChip.getStyleClass().add("card-icon-chip");

        Label badge = new Label(EnumFormatter.auctionStatusVi(status));
        badge.getStyleClass().addAll("badge", EnumFormatter.auctionStatusBadgeClass(status));

        Region topGrow = new Region();
        HBox.setHgrow(topGrow, Priority.ALWAYS);
        HBox topRow = new HBox(iconChip, topGrow, badge);
        topRow.setAlignment(Pos.CENTER);

        // 2. Tên sản phẩm
        String displayName = auction.getItemName();
        if (displayName == null || displayName.isBlank()) {
            displayName = "(Chưa có tên sản phẩm)";
        }
        Label itemName = new Label(displayName);
        itemName.getStyleClass().add("title-serif-small");
        itemName.setWrapText(true);
        itemName.setMaxHeight(52);

        // 3. Mã phiên
        Label codeLabel = new Label("Mã phiên: " + shortId(auction.getId()));
        codeLabel.getStyleClass().add("label-caption");

        Separator divider = new Separator();

        // 4. Giá — nhãn động theo trạng thái
        String priceCaption;
        long priceVal;
        if (status == AuctionStatus.OPEN) {
            priceCaption = "Giá khởi điểm";
            priceVal = auction.getStartPrice();
        } else if (status == AuctionStatus.FINISHED || status == AuctionStatus.PAID) {
            priceCaption = "Giá chốt";
            priceVal = auction.getCurrentPrice();
        } else {
            priceCaption = "Giá hiện tại";
            priceVal = auction.getCurrentPrice();
        }
        Label priceLabel = new Label(priceCaption);
        priceLabel.getStyleClass().add("label-caption");
        Label priceValue = new Label(MoneyFormatter.formatVnd(priceVal));
        priceValue.getStyleClass().add("price-medium");

        // 5. Hàng thông tin: đồng hồ + bước giá / "mở sau" / người thắng
        HBox infoRow = new HBox(8);
        infoRow.setAlignment(Pos.CENTER_LEFT);
        Label info = new Label();
        if (status == AuctionStatus.OPEN) {
            Duration toStart = Duration.between(LocalDateTime.now(), auction.getStartTime());
            if (toStart.isNegative() || toStart.isZero()) {
                info.setText("⏱  Đang bắt đầu...");
                info.getStyleClass().add("countdown-warning");
            } else {
                info.setText("⏱  Mở sau " + CountdownUtil.formatRemaining(toStart));
                info.getStyleClass().add("countdown-normal-text");
            }
            infoRow.getChildren().add(info);
        } else if (status == AuctionStatus.RUNNING) {
            Duration remaining = Duration.between(LocalDateTime.now(), auction.getEndTime());
            info.setText("⏱  " + CountdownUtil.formatRemaining(remaining));
            long totalSec = remaining.getSeconds();
            if (totalSec < 60) {
                info.getStyleClass().add("countdown-urgent");
            } else if (totalSec < 300) {
                info.getStyleClass().add("countdown-warning");
            } else {
                info.getStyleClass().add("countdown-normal-text");
            }
            Region g = new Region();
            HBox.setHgrow(g, Priority.ALWAYS);
            Label step = new Label("+" + MoneyFormatter.formatVndShort(auction.getMinIncrement()) + "/bước");
            step.getStyleClass().add("label-caption");
            infoRow.getChildren().addAll(info, g, step);
        } else {
            // FINISHED / PAID / CANCELED
            String winner = auction.getHighestBidderName();
            info.setText(winner != null && !winner.isBlank()
                    ? "Người thắng: " + winner
                    : "Không có người thắng");
            info.getStyleClass().add("text-muted");
            infoRow.getChildren().add(info);
        }

        // 6. Nút theo trạng thái: RUNNING → "Vào đấu giá", còn lại → "Xem chi tiết"
        Button actionBtn;
        if (status == AuctionStatus.RUNNING) {
            actionBtn = new Button("Vào đấu giá");
            actionBtn.getStyleClass().add("btn-primary");
        } else {
            actionBtn = new Button("Xem chi tiết");
            actionBtn.getStyleClass().add("btn-outline");
        }
        actionBtn.setMaxWidth(Double.MAX_VALUE);
        actionBtn.setOnAction(e -> {
            if (onViewDetail != null) onViewDetail.accept(auction);
        });

        card.getChildren().addAll(
                topRow, itemName, codeLabel, divider,
                priceLabel, priceValue, infoRow, actionBtn
        );

        return card;
    }

    private static String shortId(String id) {
        if (id == null) return "---";
        return id.length() > 6 ? id.substring(0, 6).toUpperCase() : id.toUpperCase();
    }
}