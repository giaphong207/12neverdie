package com.auction.client.controller;

import com.auction.client.chart.BidHistorySeriesBuilder;
import com.auction.client.context.ClientSession;
import com.auction.client.network.ServerConnection;
import com.auction.client.realtime.AuctionEventBus;
import com.auction.client.realtime.AuctionEventObserver;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.CountdownUtil;
import com.auction.client.util.MoneyParser;
import com.auction.client.util.SceneNavigator;
import com.auction.shared.exception.AppException;
import com.auction.shared.model.*;

import com.auction.shared.network.AuctionEvent;
import com.auction.shared.network.BidRequest;
import com.auction.shared.network.SubscribeAuctionRequest;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.util.Duration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public class AuctionDetailController implements AuctionEventObserver {

    @FXML private Label itemNameLabel;
    @FXML private Label descriptionLabel;
    @FXML private Label sellerLabel;
    @FXML private Label currentPriceLabel;
    @FXML private Label statusLabel;
    @FXML private Label remainingTimeLabel;
    @FXML private Label messageLabel;
    @FXML private Button placeBidButton;

    @FXML private TextField bidAmountField;
    @FXML private Label highestBidderLabel;
    @FXML private ListView<String> bidHistoryListView;

    private Auction currentAuction;
    private Timeline countdownTimeline;
    private boolean expiredHandled = false;
    private String currentAuctionId;

    @FXML private LineChart<String, Number> bidHistoryChart;
    @FXML private CategoryAxis bidTimeAxis;
    @FXML private NumberAxis bidPriceAxis;

    public void initialize() {
        // Đăng ký nhận AuctionUpdateEvent realtime từ      (tv3)
        AuctionEventBus.getInstance().addObserver(this); // (tv3)

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
        this.currentAuctionId = auctionId;
        this.expiredHandled = false;

        AuctionEventBus.getInstance().addObserver(this);

        try {
            ServerConnection.getInstance()
                    .send(new SubscribeAuctionRequest(auctionId));
        } catch (IOException e) {
            currentAuction = null;
            messageLabel.setText("Không thể tải chi tiết auction.");
            remainingTimeLabel.setText("Lỗi");
            placeBidButton.setDisable(true);
            AlertUtils.showError("Lỗi", "Không gửi được yêu cầu theo dõi auction: " + e.getMessage());
        }
    }

    public void setCurrentAuction(Auction currentAuction) {
        this.currentAuction = currentAuction;
    }

    private void renderAuction(Auction auction) {
        itemNameLabel.setText("Item ID: " + auction.getItemId());
        descriptionLabel.setText("Auction ID: " + auction.getId());
        sellerLabel.setText("Seller ID: " + auction.getSellerId());
        currentPriceLabel.setText(String.format("%,d VNĐ", auction.getCurrentPrice()));
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

        String leader = auction.getHighestBidderId();
        if (highestBidderLabel != null) {
            highestBidderLabel.setText("Người dẫn đầu: " + (leader != null ? leader : "Chưa có"));
        }

        if (bidHistoryListView != null) {
            bidHistoryListView.getItems().clear();
            List<Bid> bids = auction.getBidHistory();
            if (bids.isEmpty()) {
                bidHistoryListView.getItems().add("Chưa có ai đặt giá.");
            } else {
                for (int i = bids.size() - 1; i >= 0; i--) {
                    Bid b = bids.get(i);
                    bidHistoryListView.getItems().add(
                            b.getBidderId() + " -> " + String.format("%,d VNĐ", b.getAmount())
                    );
                }
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

        remainingTimeLabel.setText("Đã kết thúc");
        placeBidButton.setDisable(true);
        messageLabel.setText("Phiên đấu giá đã hết thời gian. Đang chờ server cập nhật trạng thái...");
    }

    private void stopCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
    }

    public void onBackClicked() {
        stopCountdown();
        AuctionEventBus.getInstance().removeObserver(this); // Anti - sniping dùng ở đây
        SceneNavigator.switchScene("/fxml/AuctionList.fxml");
    }

    public void onPlaceBidClicked() {
        if (currentAuction == null) {
            AlertUtils.showWarning("Thông báo", "Không có auction để đặt giá.");
            return;
        }

        try {
            String rawAmount = bidAmountField.getText();
            long amount = MoneyParser.parseBidAmount(rawAmount);

            User currentUser = ClientSession.getCurrentUser();
            if (currentUser == null) {
                AlertUtils.showWarning("Chưa đăng nhập", "Vui lòng đăng nhập để tham gia đấu giá.");
                return;
            }
            if (currentUser.getRole() != Role.BIDDER) {
                AlertUtils.showError("Lỗi Quyền", "Chỉ tài khoản BIDDER mới được đặt giá!");
                return;
            }

            ServerConnection.getInstance().send(new BidRequest(currentAuction.getId(),currentUser.getId(),amount));
            if (bidAmountField != null) { bidAmountField.clear(); }

            messageLabel.setText("Đã gửi yêu cầu đặt giá. Đang chờ server xử lý...");
        } catch (AppException ex) {
            AlertUtils.showError("Lỗi đặt giá", ex.getMessage());
        } catch (IOException ex) {
            AlertUtils.showError("Lỗi kết nối", "Không gửi được yêu cầu đặt giá: " + ex.getMessage());
        } catch (Exception ex) {
            AlertUtils.showError("Lỗi hệ thống", "Đã xảy ra sự cố: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Được AuctionEventBus gọi khi server broadcast AuctionUpdateEvent.
     * Xảy ra khi: có bid mới, anti-sniping gia hạn thời gian, auto-bid chạy,
     * HOẶC khi vừa subscribe (server gửi snapshot lần đầu).
     */
    @Override
    public void onAuctionUpdated(AuctionEvent event) {
        Auction updated = event.getAuction();

        // Chỉ xử lý nếu đây đúng auction mình đang xem
        // (so sánh với currentAuctionId, KHÔNG phải currentAuction vì lần đầu nó null)
        if (currentAuctionId == null || !updated.getId().equals(currentAuctionId)) {
            return;
        }

        // Wrap trong Platform.runLater để đảm bảo update UI trên FX Thread
        javafx.application.Platform.runLater(() -> {
            System.out.println("[Detail] Nhận update auction: " + updated.getId()
                    + " | endTime: " + updated.getEndTime()
                    + " | giá: " + updated.getCurrentPrice());

            // Cập nhật object auction trong bộ nhớ
            currentAuction = updated;
            expiredHandled = false;

            // Cập nhật toàn bộ UI
            renderAuction(updated);

            // Render biểu đồ bid history
            renderBidHistoryChart(updated);

            // Khởi động countdown
            startCountdown(updated.getEndTime());
        });
    }

    public void dispose() {
        AuctionEventBus.getInstance().removeObserver(this);
    }
    private void renderBidHistoryChart(Auction auction) {
        if (bidHistoryChart == null) {
            return;
        }

        bidHistoryChart.getData().clear();

        if (auction == null || auction.getBidHistory() == null || auction.getBidHistory().isEmpty()) {
            return;
        }

        XYChart.Series<String, Number> series = BidHistorySeriesBuilder.buildSeries(auction.getBidHistory());

        bidHistoryChart.getData().add(series);
    }
}