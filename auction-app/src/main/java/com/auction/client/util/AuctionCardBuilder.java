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
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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
        VBox card = new VBox(14);
        card.getStyleClass().add("auction-card");
        card.setPadding(new Insets(20));
        card.setPrefWidth(280);
        card.setMaxWidth(320);

        // 1. Badge status
        Label badge = new Label(EnumFormatter.auctionStatusVi(auction.getStatus()));
        badge.getStyleClass().addAll("badge", EnumFormatter.auctionStatusBadgeClass(auction.getStatus()));

        HBox badgeRow = new HBox(badge);
        badgeRow.setAlignment(Pos.CENTER_LEFT);

        // 2. Placeholder ảnh (chức năng ảnh thật chưa làm)
        StackPane imageBox = new StackPane();
        imageBox.setPrefHeight(160);
        imageBox.getStyleClass().add("image-placeholder");
        Label imgPlaceholder = new Label("●");
        imgPlaceholder.getStyleClass().add("image-placeholder-icon");
        imageBox.getChildren().add(imgPlaceholder);

        // 3. Tên thật + mã phiên ngắn
        String displayName = auction.getItemName();
        if (displayName == null || displayName.isBlank()) {
            displayName = "(Chưa có tên sản phẩm)";
        }
        Label itemName = new Label(displayName);
        itemName.getStyleClass().add("title-medium");
        itemName.setWrapText(true);
        itemName.setMaxHeight(48);

        Label lotLabel = new Label("Mã phiên: " + shortId(auction.getId()));
        lotLabel.getStyleClass().add("label-caption");

        // 4. Divider
        Separator divider = new Separator();

        // 5. Price block
        Label priceLabel = new Label("GIÁ HIỆN TẠI");
        priceLabel.getStyleClass().add("label-caption");

        Label priceValue = new Label(MoneyFormatter.formatVnd(auction.getCurrentPrice()));
        priceValue.getStyleClass().add("price-medium");

        // 6. Countdown — OPEN: đếm tới start, RUNNING: đếm tới end
        Label countdown = new Label();
        AuctionStatus status = auction.getStatus();
        if (status == AuctionStatus.OPEN) {
            Duration toStart = Duration.between(LocalDateTime.now(), auction.getStartTime());
            if (toStart.isNegative() || toStart.isZero()) {
                countdown.setText("⏱  Đang bắt đầu...");
                countdown.getStyleClass().add("countdown-warning");
            } else {
                countdown.setText("⏱  Bắt đầu sau " + CountdownUtil.formatRemaining(toStart));
                countdown.getStyleClass().add("countdown-normal-text");
            }
        } else if (status == AuctionStatus.RUNNING) {
            Duration remaining = Duration.between(LocalDateTime.now(), auction.getEndTime());
            countdown.setText("⏱  Còn " + CountdownUtil.formatRemaining(remaining));

            long totalSec = remaining.getSeconds();
            if (totalSec < 60) {
                countdown.getStyleClass().add("countdown-urgent");
            } else if (totalSec < 300) {
                countdown.getStyleClass().add("countdown-warning");
            } else {
                countdown.getStyleClass().add("countdown-normal-text");
            }
        } else {
            countdown.setText("Đã kết thúc");
            countdown.getStyleClass().add("text-muted");
        }

        // 7. Button
        Button viewBtn = new Button("Xem chi tiết");
        viewBtn.getStyleClass().add("btn-outline");
        viewBtn.setMaxWidth(Double.MAX_VALUE);
        viewBtn.setOnAction(e -> {
            if (onViewDetail != null) onViewDetail.accept(auction);
        });

        Region spacer = new Region();
        spacer.setPrefHeight(4);

        card.getChildren().addAll(
                badgeRow, imageBox, itemName, lotLabel, divider,
                priceLabel, priceValue, countdown, spacer, viewBtn
        );

        return card;
    }

    private static String shortId(String id) {
        if (id == null) return "---";
        return id.length() > 6 ? id.substring(0, 6).toUpperCase() : id.toUpperCase();
    }
}