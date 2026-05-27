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
import com.auction.shared.exception.AppExceptions.*;

import com.auction.shared.factory.UserFactory;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;
import com.auction.shared.model.bid.Bid;
import com.auction.shared.model.user.Role;
import com.auction.shared.model.user.User;
import com.auction.shared.networkMessage.AuctionEvents.*;
import com.auction.shared.networkMessage.Requests.*;
import com.auction.shared.networkMessage.Results.*;
import com.auction.client.main.ClientApp;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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

import com.auction.client.util.EnumFormatter;
import com.auction.client.util.MoneyFormatter;
import com.auction.client.util.SidebarBuilder;
import com.auction.client.util.SidebarBuilder.NavKey;
import javafx.scene.layout.StackPane;
import javafx.application.Platform;
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
    @FXML private StackPane sidebarContainer;

    private Auction currentAuction;
    private Timeline countdownTimeline;
    private boolean expiredHandled = false;
    private String currentAuctionId;

    @FXML private LineChart<String, Number> bidHistoryChart;
    @FXML private CategoryAxis bidTimeAxis;
    @FXML private NumberAxis bidPriceAxis;

    public void initialize() {
        // Build sidebar theo role
        if (sidebarContainer != null && ClientSession.getCurrentUser() != null) {
            var user = ClientSession.getCurrentUser();
            NavKey activeKey = UserFactory.toRole(user) == Role.BIDDER
                    ? NavKey.BIDDER_LIVE
                    : NavKey.SELLER_AUCTIONS;
            var sidebar = SidebarBuilder.build(
                    user,
                    activeKey,
                    this::handleNavClick,
                    this::handleLogout
            );
            sidebarContainer.getChildren().add(sidebar);
        }

        AuctionEventBus.getInstance().addObserver(this);

        String auctionId = ClientSession.getSelectedAuctionId();
        if (auctionId == null || auctionId.isBlank()) {
            messageLabel.setText("Không có auction được chọn.");
            placeBidButton.setDisable(true);
            remainingTimeLabel.setText("Không có dữ liệu");
            return;
        }

        loadAuction(auctionId);
    }

    private void handleNavClick(NavKey key) {
        switch (key) {
            // Bidder routes
            case BIDDER_HOME -> SceneNavigator.switchScene("/fxml/BidderDashboard.fxml");
            case BIDDER_LIVE -> SceneNavigator.switchScene("/fxml/AuctionList.fxml");

            // Seller routes
            case SELLER_OVERVIEW -> SceneNavigator.switchScene("/fxml/SellerDashboard.fxml");
            case SELLER_PRODUCTS -> SceneNavigator.switchScene("/fxml/ProductManagement.fxml");
            case SELLER_AUCTIONS -> SceneNavigator.switchScene("/fxml/SellerAuctions.fxml");

            // Admin routes
            case ADMIN_OVERVIEW -> SceneNavigator.switchScene("/fxml/AdminDashboard.fxml");
            case ADMIN_AUCTIONS -> SceneNavigator.switchScene("/fxml/AuctionList.fxml");

            default -> AlertUtils.showInfo("Sắp ra mắt", "Tính năng này đang được phát triển.");
        }
    }

    private void handleLogout() {
        ClientSession.clear();
        SceneNavigator.switchScene("/fxml/Login.fxml");
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
        itemNameLabel.setText("LOT №" + shortId(auction.getItemId()));
        descriptionLabel.setText("Mã phiên: " + auction.getId());
        sellerLabel.setText(auction.getSellerId());
        currentPriceLabel.setText(MoneyFormatter.formatVnd(auction.getCurrentPrice()));

        // Status badge với style class tương ứng
        statusLabel.setText(EnumFormatter.auctionStatusVi(auction.getStatus()));
        statusLabel.getStyleClass().removeAll(
                "badge-open", "badge-running", "badge-finished", "badge-paid", "badge-canceled");
        statusLabel.getStyleClass().add(EnumFormatter.auctionStatusBadgeClass(auction.getStatus()));

        if (auction.isFinished()) {
            remainingTimeLabel.setText("Đã kết thúc");
            placeBidButton.setDisable(true);
            if (auction.getWinnerBidderId() != null) {
                messageLabel.setText("Người thắng: " + auction.getWinnerBidderId());
            } else {
                messageLabel.setText("Phiên đã kết thúc — chưa có người thắng");
            }
        } else {
            placeBidButton.setDisable(auction.getStatus() != AuctionStatus.RUNNING);
            if (auction.getHighestBidderId() != null) {
                messageLabel.setText("Đang dẫn đầu: " + auction.getHighestBidderId());
            } else {
                messageLabel.setText("Chưa có ai đặt giá");
            }
        }

        String leader = auction.getHighestBidderId();
        if (highestBidderLabel != null) {
            highestBidderLabel.setText("● Người dẫn đầu: " + (leader != null ? leader : "Chưa có"));
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
                            b.getBidderId() + "   →   " + MoneyFormatter.formatVnd(b.getAmount())
                    );
                }
            }
        }
    }

    private String shortId(String id) {
        if (id == null) return "---";
        return id.length() > 8 ? id.substring(0, 8) : id;
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

        // Đổi màu theo trạng thái: bình thường / cảnh báo / khẩn cấp
        remainingTimeLabel.getStyleClass().removeAll(
                "countdown-normal", "countdown-warning", "countdown-emergency");
        long totalSec = remaining.getSeconds();
        if (totalSec < 60) {
            remainingTimeLabel.getStyleClass().add("countdown-emergency");
        } else if (totalSec < 300) {
            remainingTimeLabel.getStyleClass().add("countdown-warning");
        } else {
            remainingTimeLabel.getStyleClass().add("countdown-normal");
        }
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

        // === Phần 1: Validate đồng bộ (chạy trên FX thread) ===
        final long amount;
        final User currentUser;
        try {
            String rawAmount = bidAmountField.getText();
            amount = MoneyParser.parseBidAmount(rawAmount);

            currentUser = ClientSession.getCurrentUser();
            if (currentUser == null) {
                AlertUtils.showWarning("Chưa đăng nhập", "Vui lòng đăng nhập để tham gia đấu giá.");
                return;
            }
            if (UserFactory.toRole(currentUser) != Role.BIDDER) {
                AlertUtils.showError("Lỗi Quyền", "Chỉ tài khoản BIDDER mới được đặt giá!");
                return;
            }
        } catch (AppException ex) {
            AlertUtils.showError("Lỗi đặt giá", ex.getMessage());
            return;
        }

        // === Phần 2: Update UI báo "đang chờ" ===
        bidAmountField.clear();
        messageLabel.setText("Đã gửi yêu cầu đặt giá. Đang chờ server xử lý...");

        // === Phần 3: Gửi request + đợi response trên thread riêng ===
        final String auctionId = currentAuction.getId();
        final String bidderId = currentUser.getId();

        new Thread(() -> {
            try {
                ServerConnection.getInstance().send(new BidRequest(auctionId, bidderId, amount));
                Object response = ClientApp.getListener().waitForResponse();

                Platform.runLater(() -> handleBidResult(response));

            } catch (IOException ex) {
                Platform.runLater(() -> {
                    AlertUtils.showError("Lỗi kết nối", "Không gửi được yêu cầu: " + ex.getMessage());
                    messageLabel.setText("");
                });
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                Platform.runLater(() -> {
                    AlertUtils.showError("Bị gián đoạn", "Yêu cầu bị huỷ.");
                    messageLabel.setText("");
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    AlertUtils.showError("Lỗi hệ thống", "Đã xảy ra sự cố: " + ex.getMessage());
                    messageLabel.setText("");
                });
            }
        }).start();
    }

    private void handleBidResult(Object response) {
        if (response instanceof BidResult result) {
            switch (result) {
                case BidResult.Success s -> {
                    messageLabel.setText("Đặt giá thành công!");
                    // UI sẽ tự update qua AuctionUpdatedEvent (broadcast)
                }
                case BidResult.Failure f -> {
                    AlertUtils.showError("Đặt giá thất bại", f.reason());
                    messageLabel.setText("");
                }
            }
        } else {
            AlertUtils.showError("Lỗi", "Phản hồi không hợp lệ từ server: " + response.getClass().getSimpleName());
            messageLabel.setText("");
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