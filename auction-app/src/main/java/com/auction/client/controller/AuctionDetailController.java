package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.util.CountdownUtil;
import com.auction.client.util.SceneNavigator;
import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.FileAuctionDao;
import com.auction.server.service.AuctionLifecycleService;
import com.auction.server.service.DefaultAuctionLifecycleService;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.util.Duration;

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

    @FXML
    private TextField bidAmountField;

    private final AuctionDao auctionDao = new FileAuctionDao();
    private final AuctionLifecycleService lifecycleService =
            new DefaultAuctionLifecycleService(auctionDao);

    private Auction currentAuction;
    private Timeline countdownTimeline;
    private boolean expiredHandled = false;

    public void initialize() {
        String auctionId = ClientSession.getSelectedAuctionId();

        if (auctionId == null || auctionId.isBlank()) {
            messageLabel.setText("Không có auction được chọn.");
            placeBidButton.setDisable(true);
            remainingTimeLabel.setText("Không có dữ liệu");
            return;
        }

        loadAuction(auctionId);
    }

    public void loadAuction(String auctionId) {
        try {
            currentAuction = lifecycleService.updateStatusByTime(auctionId);
            expiredHandled = false;
            renderAuction(currentAuction);
            startCountdown(currentAuction.getEndTime());
        } catch (Exception e) {
            currentAuction = null;
            messageLabel.setText("Không thể tải chi tiết auction.");
            remainingTimeLabel.setText("Lỗi");
            placeBidButton.setDisable(true);
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không tải được auction: " + e.getMessage());
        }
    }

    private void renderAuction(Auction auction) {
        itemNameLabel.setText("Item ID: " + auction.getItemId());
        descriptionLabel.setText("Auction ID: " + auction.getId());
        sellerLabel.setText("Seller ID: " + auction.getSellerId());
        currentPriceLabel.setText(formatMoney(auction.getCurrentPrice()));
        statusLabel.setText(auction.getStatus().name());

        if (auction.isFinished()) {
            remainingTimeLabel.setText("Đã kết thúc");
            placeBidButton.setDisable(true);

            if (auction.getWinnerBidderId() != null) {
                messageLabel.setText("Người thắng: " + auction.getWinnerBidderId());
            } else {
                messageLabel.setText("Chưa có người thắng");
            }
        } else {
            placeBidButton.setDisable(auction.getStatus() != AuctionStatus.RUNNING);

            if (auction.getHighestBidderId() != null) {
                messageLabel.setText("Người đang dẫn đầu: " + auction.getHighestBidderId());
            } else {
                messageLabel.setText("Chưa có ai đặt giá");
            }
        }
    }

    private void startCountdown(LocalDateTime endTime) {
        stopCountdown();

        if (currentAuction == null || currentAuction.isFinished()) {
            remainingTimeLabel.setText("Đã kết thúc");
            placeBidButton.setDisable(true);
            return;
        }

        updateRemainingTime();

        countdownTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1), event -> updateRemainingTime())
        );
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    private void updateRemainingTime() {
        if (currentAuction == null) {
            remainingTimeLabel.setText("Không có dữ liệu");
            placeBidButton.setDisable(true);
            return;
        }

        java.time.Duration remaining =
                java.time.Duration.between(LocalDateTime.now(), currentAuction.getEndTime());

        if (remaining.isZero() || remaining.isNegative()) {
            handleAuctionExpired();
            return;
        }

        remainingTimeLabel.setText(CountdownUtil.formatRemaining(remaining));
    }

    private void handleAuctionExpired() {
        if (expiredHandled || currentAuction == null) {
            return;
        }

        expiredHandled = true;
        stopCountdown();

        try {
            currentAuction = lifecycleService.updateStatusByTime(currentAuction.getId());
            renderAuction(currentAuction);
            remainingTimeLabel.setText("Đã kết thúc");
            placeBidButton.setDisable(true);

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Phiên đã kết thúc",
                    "Phiên đấu giá đã hết thời gian."
            );
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không cập nhật được trạng thái auction.");
        }
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
        if (currentAuction == null) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Không có auction để đặt giá.");
            return;
        }

        try {
            currentAuction = lifecycleService.updateStatusByTime(currentAuction.getId());
            renderAuction(currentAuction);

            if (!currentAuction.isRunning()) {
                showAlert(Alert.AlertType.WARNING, "Thông báo", "Phiên đấu giá đã đóng.");
                return;
            }

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Thông báo",
                    "Tuần 3 phần đặt giá thật do TV3 làm. TV4 chỉ khóa bid khi phiên hết giờ."
            );
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không xử lý được trạng thái auction.");
        }
    }

    private String formatMoney(long amount) {
        return String.format("%,d VNĐ", amount);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}