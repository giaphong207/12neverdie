package com.auction.client.util;

import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * Tạo card hiển thị 1 phiên đấu giá:
 * ┌─────────────────────────┐
 * │ [● ĐANG DIỄN RA badge ] │
 * │ ▒▒▒▒▒ ảnh giả ▒▒▒▒▒▒    │
 * │ Tên sản phẩm            │
 * │ LOT №abc123             │
 * │ ─────────────           │
 * │ GIÁ HIỆN TẠI            │
 * │ 1.250.000.000 ₫         │
 * │ ⏱ 02:14:35              │
 * │ [ XEM CHI TIẾT  ]       │
 * └─────────────────────────┘
 */
public final class AuctionCardBuilder {

    private AuctionCardBuilder() {}

    public static VBox build(Auction auction, Consumer<Auction> onViewDetail) {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(20));
        card.setPrefWidth(280);
        card.setMaxWidth(320);

        // 1. Badge status
        Label badge = new Label(EnumFormatter.auctionStatusVi(auction.getStatus()));
        badge.getStyleClass().addAll("badge", EnumFormatter.auctionStatusBadgeClass(auction.getStatus()));

        HBox badgeRow = new HBox(badge);
        badgeRow.setAlignment(Pos.CENTER_LEFT);

        // 2. Placeholder ảnh (vì project không có ảnh thật)
        StackPane imageBox = new StackPane();
        imageBox.setPrefHeight(160);
        imageBox.setStyle("-fx-background-color: -fx-bg-hover; -fx-background-radius: 4px;");
        Label imgPlaceholder = new Label("●");
        imgPlaceholder.setStyle("-fx-font-size: 36px; -fx-text-fill: -fx-text-tertiary;");
        imageBox.getChildren().add(imgPlaceholder);

        // 3. Tên + LOT
        Label itemName = new Label(shortItemName(auction));
        itemName.getStyleClass().add("title-serif-small");
        itemName.setWrapText(true);
        itemName.setMaxHeight(48);

        Label lotLabel = new Label("LOT №" + shortId(auction.getId()));
        lotLabel.getStyleClass().add("label-tiny-uppercase");

        // 4. Divider
        Separator divider = new Separator();
        divider.getStyleClass().add("hairline-divider");

        // 5. Price block
        Label priceLabel = new Label("GIÁ HIỆN TẠI");
        priceLabel.getStyleClass().add("label-tiny-uppercase");

        Label priceValue = new Label(MoneyFormatter.formatVnd(auction.getCurrentPrice()));
        priceValue.getStyleClass().add("price-medium");
        priceValue.setStyle("-fx-text-fill: -fx-champagne;");

        // 6. Countdown (chỉ hiển thị nếu phiên còn chạy)
        Label countdown = new Label();
        if (!auction.isFinished() && auction.getStatus() != AuctionStatus.CANCELED) {
            Duration remaining = Duration.between(LocalDateTime.now(), auction.getEndTime());
            countdown.setText("⏱  " + CountdownUtil.formatRemaining(remaining));

            long totalSec = remaining.getSeconds();
            if (totalSec < 60) {
                countdown.setStyle("-fx-text-fill: -fx-bordeaux; -fx-font-size: 13px; -fx-font-weight: 600;");
            } else if (totalSec < 300) {
                countdown.setStyle("-fx-text-fill: -fx-champagne; -fx-font-size: 13px; -fx-font-weight: 600;");
            } else {
                countdown.setStyle("-fx-text-fill: -fx-text-secondary; -fx-font-size: 13px;");
            }
        } else {
            countdown.setText("Đã kết thúc");
            countdown.setStyle("-fx-text-fill: -fx-text-tertiary; -fx-font-size: 12px;");
        }

        // 7. Button
        Button viewBtn = new Button("XEM CHI TIẾT");
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

    private static String shortItemName(Auction auction) {
        if (auction.getItemId() == null) return "Sản phẩm chưa rõ tên";
        return "LOT phẩm №" + shortId(auction.getItemId());
    }

    private static String shortId(String id) {
        if (id == null) return "---";
        return id.length() > 6 ? id.substring(0, 6).toUpperCase() : id.toUpperCase();
    }
}