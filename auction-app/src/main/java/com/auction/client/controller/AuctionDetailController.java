package com.auction.client.controller;

import com.auction.client.util.CountdownUtil;
import com.auction.client.util.SceneNavigator;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.time.LocalDateTime;

public class AuctionDetailController {

    @FXML
    private Label itemNameLabel;

    @FXML
    private Label descriptionLabel;

    @FXML
    private Label sellerLabel;

    @FXML
    private Label currentPriceLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label remainingTimeLabel;

    @FXML
    private Label messageLabel;

    @FXML
    private Button placeBidButton;

    private Timeline countdownTimeline;
    private LocalDateTime currentEndTime;

    public void initialize() {
        loadAuction("MOCK-AUCTION-001");
    }

    public void loadAuction(String auctionId) {
        messageLabel.setText("Đang mở chi tiết auction: " + auctionId + " (mock tuần 2)");

        itemNameLabel.setText("iPhone 15 Pro Max");
        descriptionLabel.setText("Máy mới 99%, còn hộp, pin tốt.");
        sellerLabel.setText("seller_demo");
        currentPriceLabel.setText("20,000,000 VNĐ");
        statusLabel.setText("RUNNING");

        currentEndTime = LocalDateTime.now().plusMinutes(5).plusSeconds(10);
        startCountdown(currentEndTime);
    }

    private void startCountdown(LocalDateTime endTime) {
        stopCountdown();
        currentEndTime = endTime;
        updateRemainingTime();

        countdownTimeline = new Timeline(
                new KeyFrame(javafx.util.Duration.seconds(1), event -> updateRemainingTime())
        );
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    private void updateRemainingTime() {
        if (currentEndTime == null) {
            remainingTimeLabel.setText("Không có dữ liệu thời gian");
            placeBidButton.setDisable(true);
            return;
        }

        java.time.Duration remaining = java.time.Duration.between(LocalDateTime.now(), currentEndTime);

        if (remaining.isZero() || remaining.isNegative()) {
            remainingTimeLabel.setText("Đã kết thúc");
            statusLabel.setText("FINISHED");
            placeBidButton.setDisable(true);
            messageLabel.setText("Phiên đấu giá đã kết thúc.");
            stopCountdown();
            return;
        }

        remainingTimeLabel.setText(CountdownUtil.formatRemaining(remaining));
        placeBidButton.setDisable(false);
    }

    private void stopCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
    }

    public void onBackClicked() {
        stopCountdown();
        SceneNavigator.switchScene("/fxml/AuctionList.fxml");
    }

    public void onPlaceBidClicked() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông báo");
        alert.setHeaderText("Chưa làm ở tuần 2");
        alert.setContentText("Chức năng Place Bid sẽ làm ở tuần 3.");
        alert.showAndWait();
    }
}