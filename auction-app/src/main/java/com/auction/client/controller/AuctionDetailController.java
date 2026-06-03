package com.auction.client.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auction.client.chart.BidHistorySeriesBuilder;
import com.auction.client.context.ClientSession;
import com.auction.client.network.ServerConnection;
import com.auction.client.realtime.AuctionEventBus;
import com.auction.client.realtime.AuctionEventObserver;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.AutoBidDialogFactory;
import com.auction.client.util.CountdownUtil;
import com.auction.client.util.Disposable;
import com.auction.client.util.EnumFormatter;
import com.auction.client.util.MoneyFormatter;
import com.auction.client.util.MoneyParser;
import com.auction.client.util.NavRouter;
import com.auction.client.util.RequestExecutor;
import com.auction.client.util.SceneNavigator;
import com.auction.client.util.SidebarBuilder.NavKey;
import com.auction.client.util.TopbarBuilder;
import com.auction.shared.exception.AppExceptions.AppException;
import com.auction.shared.factory.UserFactory;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;
import com.auction.shared.model.bid.Bid;
import com.auction.shared.model.bid.BidSource;
import com.auction.shared.model.user.Role;
import com.auction.shared.model.user.User;
import com.auction.shared.networkMessage.AuctionEvents.*;
import com.auction.shared.networkMessage.AuctionEvents.AuctionCancelledEvent;
import com.auction.shared.networkMessage.AuctionEvents.AuctionEndedEvent;
import com.auction.shared.networkMessage.AuctionEvents.AuctionEvent;
import com.auction.shared.networkMessage.AuctionEvents.AuctionExtendedEvent;
import com.auction.shared.networkMessage.AuctionEvents.AuctionPaidEvent;
import com.auction.shared.networkMessage.Requests.*;
import com.auction.shared.networkMessage.Requests.BidRequest;
import com.auction.shared.networkMessage.Requests.CancelAuctionRequest;
import com.auction.shared.networkMessage.Requests.DisableAutoBidRequest;
import com.auction.shared.networkMessage.Requests.SetAutoBidRequest;
import com.auction.shared.networkMessage.Requests.SubscribeAuctionRequest;
import com.auction.shared.networkMessage.Results.*;
import com.auction.shared.networkMessage.Results.BidResult;
import com.auction.shared.networkMessage.Results.CancelAuctionResult;
import com.auction.shared.networkMessage.Results.SetAutoBidResponse;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class AuctionDetailController implements AuctionEventObserver, Disposable {

    @FXML private Label itemNameLabel;
    @FXML private Label auctionCodeLabel;
    @FXML private Label descriptionLabel;
    @FXML private Label sellerLabel;
    @FXML private Label currentPriceLabel;
    @FXML private Label statusLabel;
    @FXML private Label remainingTimeLabel;
    @FXML private Label countdownCaptionLabel;
    @FXML private Label messageLabel;
    @FXML private Button placeBidButton;
    @FXML private Button configureAutoBidButton;
    @FXML private Button cancelAuctionButton;

    @FXML private TextField bidAmountField;
    @FXML private Label minBidLabel;
    @FXML private Label highestBidderLabel;
    @FXML private ListView<Bid> bidHistoryListView;
    @FXML private StackPane topbarContainer;

    /**
     * Container bọc toàn bộ khu vực đặt giá (bid panel).
     * Được ẩn hoàn toàn khi user không phải BIDDER (SELLER hoặc ADMIN).
     * fx:id="bidPanel" trong AuctionDetail.fxml.
     */
    @FXML private VBox bidPanel;

    private Auction currentAuction;
    private Timeline countdownTimeline;
    private boolean expiredHandled = false;
    private String currentAuctionId;

    private static final Logger log = LoggerFactory.getLogger(AuctionDetailController.class);

    @FXML private LineChart<String, Number> bidHistoryChart;
    @FXML private CategoryAxis bidTimeAxis;
    @FXML private NumberAxis bidPriceAxis;
    private static final String MANUAL_COLOR = "#E24B4A"; // đỏ — thủ công
    private static final String AUTO_COLOR   = "#378ADD"; // xanh — tự động

    public void initialize() {
        // Build topbar theo role
        if (topbarContainer != null && ClientSession.getCurrentUser() != null) {
            var user = ClientSession.getCurrentUser();
            NavKey activeKey = resolveActiveNavKey(user);
            var topbar = TopbarBuilder.build(
                    user,
                    activeKey,
                    this::handleNavClick,
                    this::handleLogout
            );
            topbarContainer.getChildren().add(topbar);
        }

        // Ẩn/hiện bidPanel ngay từ đầu theo role — không cần đợi auction load
        setupRoleBasedUI();

        // Cấu hình list lịch sử: mỗi dòng = tên (trái) + nhãn AUTO + số tiền (phải)
        if (bidHistoryListView != null) {
            bidHistoryListView.setPlaceholder(new Label("Chưa có ai đặt giá."));
            bidHistoryListView.setCellFactory(lv -> new ListCell<>() {
                @Override protected void updateItem(Bid b, boolean empty) {
                    super.updateItem(b, empty);
                    if (empty || b == null) {
                        setGraphic(null);
                        setText(null);
                        return;
                    }
                    String name = (b.getBidderName() != null && !b.getBidderName().isBlank())
                            ? b.getBidderName() : b.getBidderId();
                    Label nameLabel = new Label(name);
                    nameLabel.getStyleClass().add("text-body");

                    HBox row = new HBox(8);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.getChildren().add(nameLabel);

                    if (b.getSource() == BidSource.AUTO) {
                        Label autoTag = new Label("AUTO");
                        autoTag.getStyleClass().add("auto-tag");
                        row.getChildren().add(autoTag);
                    }

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    Label amount = new Label(MoneyFormatter.formatVnd(b.getAmount()));
                    amount.getStyleClass().add("bid-amount");
                    row.getChildren().addAll(spacer, amount);

                    setGraphic(row);
                    setText(null);
                }
            });
        }

        AuctionEventBus.getInstance().addObserver(this);

        String auctionId = ClientSession.getSelectedAuctionId();
        if (auctionId == null || auctionId.isBlank()) {
            if (messageLabel != null) messageLabel.setText("Không có auction được chọn.");
            if (placeBidButton != null) placeBidButton.setDisable(true);
            if (remainingTimeLabel != null) remainingTimeLabel.setText("Không có dữ liệu");
            return;
        }

        loadAuction(auctionId);
    }

    /**
     * Ẩn hoàn toàn bidPanel nếu user là SELLER hoặc ADMIN.
     * Chỉ BIDDER mới thấy khu vực đặt giá.
     *
     * Gọi một lần trong initialize() — không cần gọi lại mỗi khi renderAuction()
     * vì role không thay đổi trong suốt session.
     */
    private void setupRoleBasedUI() {
        if (bidPanel == null) return;
        User user = ClientSession.getCurrentUser();
        boolean isBidder = (user != null) && (UserFactory.toRole(user) == Role.BIDDER);
        bidPanel.setVisible(isBidder);
        bidPanel.setManaged(isBidder);
    }

    /**
     * Chọn NavKey active phù hợp theo role.
     * BIDDER → BIDDER_LIVE, SELLER → SELLER_AUCTIONS, ADMIN → ADMIN_AUCTIONS.
     */
    private NavKey resolveActiveNavKey(User user) {
        return switch (UserFactory.toRole(user)) {
            case BIDDER -> NavKey.BIDDER_LIVE;
            case SELLER -> NavKey.SELLER_AUCTIONS;
            case ADMIN  -> NavKey.ADMIN_AUCTIONS;
        };
    }

    private void handleNavClick(NavKey key) {
        NavRouter.route(key);
    }

    public void loadAuction(String auctionId) {
        this.currentAuctionId = auctionId;
        this.expiredHandled = false;

        try {
            ServerConnection.getInstance()
                    .send(new SubscribeAuctionRequest(auctionId));
        } catch (IOException e) {
            currentAuction = null;
            if (messageLabel != null) messageLabel.setText("Không thể tải chi tiết auction.");
            if (remainingTimeLabel != null) remainingTimeLabel.setText("Lỗi");
            if (placeBidButton != null) placeBidButton.setDisable(true);
            AlertUtils.showError("Lỗi", "Không gửi được yêu cầu theo dõi auction: " + e.getMessage());
        }
    }

    public void setCurrentAuction(Auction currentAuction) {
        this.currentAuction = currentAuction;
    }

    private void renderAuction(Auction auction) {
        // Tên + mô tả thật từ Item (server đã enrich)
        String itemName = auction.getItemName();
        itemNameLabel.setText(itemName != null && !itemName.isBlank()
                ? itemName
                : "(Chưa có tên sản phẩm)");

        if (auctionCodeLabel != null) {
            auctionCodeLabel.setText("Mã phiên: " + shortId(auction.getId()));
        }

        String desc = auction.getItemDescription();
        descriptionLabel.setText(desc != null && !desc.isBlank()
                ? desc
                : "(chưa có mô tả)");

        String sellerName = auction.getSellerName();
        sellerLabel.setText(sellerName != null && !sellerName.isBlank()
                ? sellerName
                : auction.getSellerId());

        currentPriceLabel.setText(MoneyFormatter.formatVnd(auction.getCurrentPrice()));

        // Status badge với style class tương ứng
        statusLabel.setText(EnumFormatter.auctionStatusVi(auction.getStatus()));
        statusLabel.getStyleClass().removeAll(
                "badge-open", "badge-running", "badge-finished", "badge-paid", "badge-canceled");
        statusLabel.getStyleClass().add(EnumFormatter.auctionStatusBadgeClass(auction.getStatus()));

        // Tối thiểu = currentPrice + minIncrement (chỉ ý nghĩa khi RUNNING)
        long minNext = auction.getCurrentPrice() + auction.getMinIncrement();
        if (minBidLabel != null) {
            minBidLabel.setText("Tối thiểu: " + MoneyFormatter.formatVnd(minNext));
        }
        if (bidAmountField != null) {
            bidAmountField.setPromptText(MoneyFormatter.formatVnd(minNext) + " trở lên");
        }

        // Tên thật người dẫn đầu (server enrich)
        String leaderName = auction.getHighestBidderName();
        String leaderDisplay = (leaderName != null && !leaderName.isBlank())
                ? leaderName
                : auction.getHighestBidderId();

        // Auto-bid CHỈ cho phép khi RUNNING — đồng bộ với chốt chặn ở server.
        boolean isRunning = auction.getStatus() == AuctionStatus.RUNNING;
        if (configureAutoBidButton != null) {
            configureAutoBidButton.setDisable(!isRunning);
        }

        if (auction.isFinished()) {
            if (remainingTimeLabel != null) remainingTimeLabel.setText("Đã kết thúc");
            if (placeBidButton != null) placeBidButton.setDisable(true);
            if (messageLabel != null) {
                if (auction.getWinnerBidderId() != null) {
                    messageLabel.setText("Người thắng: " + leaderDisplay);
                } else {
                    messageLabel.setText("Phiên đã kết thúc — chưa có người thắng");
                }
            }
        } else {
            if (placeBidButton != null) placeBidButton.setDisable(!isRunning);
            if (messageLabel != null) {
                if (auction.getHighestBidderId() != null) {
                    messageLabel.setText("Đang dẫn đầu: " + leaderDisplay);
                } else {
                    messageLabel.setText("Chưa có ai đặt giá");
                }
            }
        }

        if (highestBidderLabel != null) {
            highestBidderLabel.setText("● Người dẫn đầu: "
                    + (auction.getHighestBidderId() != null ? leaderDisplay : "Chưa có"));
        }

        if (bidHistoryListView != null) {
            List<Bid> bids = auction.getBidHistory();
            // Mới nhất lên trên
            bidHistoryListView.getItems().clear();
            for (int i = bids.size() - 1; i >= 0; i--) {
                bidHistoryListView.getItems().add(bids.get(i));
            }
        }

        updateCancelButtonVisibility(auction);
    }

    /**
     * Nút Hủy chỉ hiển thị khi user có canManage() = true (admin / seller chủ phiên)
     * VÀ phiên đang ở trạng thái có thể hủy (OPEN hoặc RUNNING).
     */
    private void updateCancelButtonVisibility(Auction auction) {
        if (cancelAuctionButton == null) return;
        User user = ClientSession.getCurrentUser();
        boolean canManage = user != null && user.canManage(auction);
        boolean cancelable = auction.getStatus() == AuctionStatus.OPEN
                || auction.getStatus() == AuctionStatus.RUNNING;
        boolean show = canManage && cancelable;
        cancelAuctionButton.setVisible(show);
        cancelAuctionButton.setManaged(show);
    }

    private String shortId(String id) {
        if (id == null) return "---";
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    private void startCountdown() {
        stopCountdown();

        if (currentAuction == null || currentAuction.isFinished()) {
            if (remainingTimeLabel != null) remainingTimeLabel.setText("Đã kết thúc");
            if (placeBidButton != null) placeBidButton.setDisable(true);
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
            if (remainingTimeLabel != null) remainingTimeLabel.setText("Không có dữ liệu");
            if (placeBidButton != null) placeBidButton.setDisable(true);
            return;
        }

        boolean notStartedYet = currentAuction.getStatus() == AuctionStatus.OPEN;
        LocalDateTime target = notStartedYet
                ? currentAuction.getStartTime()
                : currentAuction.getEndTime();

        if (countdownCaptionLabel != null) {
            countdownCaptionLabel.setText(notStartedYet ? "BẮT ĐẦU SAU" : "KẾT THÚC SAU");
        }

        java.time.Duration remaining = java.time.Duration.between(LocalDateTime.now(), target);

        if (remaining.isZero() || remaining.isNegative()) {
            if (notStartedYet) {
                if (remainingTimeLabel != null) remainingTimeLabel.setText("Đang bắt đầu...");
                return;
            }
            handleAuctionExpired();
            return;
        }

        if (remainingTimeLabel != null) remainingTimeLabel.setText(CountdownUtil.formatRemaining(remaining));

        if (remainingTimeLabel != null) {
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
    }

    private void handleAuctionExpired() {
        if (expiredHandled || currentAuction == null) return;
        expiredHandled = true;
        stopCountdown();
        if (remainingTimeLabel != null) remainingTimeLabel.setText("Đã kết thúc");
        if (placeBidButton != null) placeBidButton.setDisable(true);
        if (messageLabel != null) messageLabel.setText("Phiên đấu giá đã hết thời gian. Đang chờ server cập nhật trạng thái...");
    }

    private void stopCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
    }

    public void onBackClicked() {
        SceneNavigator.switchScene("/fxml/AuctionList.fxml");
    }

    public void onPlaceBidClicked() {
        if (currentAuction == null) {
            AlertUtils.showWarning("Thông báo", "Không có auction để đặt giá.");
            return;
        }

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

        bidAmountField.clear();
        if (messageLabel != null) messageLabel.setText("Đã gửi yêu cầu đặt giá. Đang chờ server xử lý...");

        final String auctionId = currentAuction.getId();
        final String bidderId = currentUser.getId();

        RequestExecutor.send(
                new BidRequest(auctionId, bidderId, amount),
                response -> handleBidResult(response),
                error -> {
                    AlertUtils.showError("Đặt giá thất bại", error);
                    if (messageLabel != null) messageLabel.setText("");
                }
        );
    }

    private void handleBidResult(Object response) {
        if (response instanceof BidResult result) {
            switch (result) {
                case BidResult.Success s -> {
                    if (messageLabel != null) messageLabel.setText("Đặt giá thành công!");
                }
                case BidResult.Failure f -> {
                    AlertUtils.showError("Đặt giá thất bại", f.reason());
                    if (messageLabel != null) messageLabel.setText("");
                }
            }
        } else {
            AlertUtils.showError("Lỗi", "Phản hồi không hợp lệ từ server: " + response.getClass().getSimpleName());
            if (messageLabel != null) messageLabel.setText("");
        }
    }

    public void onConfigureAutoBidClicked() {
        if (currentAuction == null) {
            AlertUtils.showWarning("Thông báo", "Không có auction để cấu hình.");
            return;
        }

        User currentUser = ClientSession.getCurrentUser();
        if (currentUser == null) {
            AlertUtils.showWarning("Chưa đăng nhập", "Vui lòng đăng nhập để dùng đấu giá tự động.");
            return;
        }
        if (UserFactory.toRole(currentUser) != Role.BIDDER) {
            AlertUtils.showError("Lỗi Quyền", "Chỉ tài khoản BIDDER mới được đặt đấu giá tự động!");
            return;
        }

        var result = AutoBidDialogFactory.showDialog();
        if (result.isEmpty()) return;

        final long maxAmount = result.get().maxAmount;
        final long increment = result.get().increment;
        final String auctionId = currentAuction.getId();
        final String bidderId  = currentUser.getId();

        if (messageLabel != null) messageLabel.setText("Đang thiết lập đấu giá tự động...");

        RequestExecutor.send(
                new SetAutoBidRequest(auctionId, bidderId, maxAmount, increment),
                this::handleSetAutoBidResponse,
                error -> {
                    AlertUtils.showError("Đấu giá tự động thất bại", error);
                    if (messageLabel != null) messageLabel.setText("");
                }
        );
    }

    private void handleSetAutoBidResponse(Object response) {
        if (response instanceof SetAutoBidResponse r) {
            if (r.success()) {
                AlertUtils.showInfo("Thành công", r.message());
                if (messageLabel != null) messageLabel.setText("Đã bật đấu giá tự động.");
            } else {
                AlertUtils.showError("Thất bại", r.message());
                if (messageLabel != null) messageLabel.setText("");
            }
        } else {
            AlertUtils.showError("Lỗi",
                    "Phản hồi không hợp lệ từ server: " + response.getClass().getSimpleName());
            if (messageLabel != null) messageLabel.setText("");
        }
    }

    public void onDisableAutoBidClicked() {
        if (currentAuction == null) {
            AlertUtils.showWarning("Thông báo", "Không có phiên.");
            return;
        }
        User currentUser = ClientSession.getCurrentUser();
        if (currentUser == null) {
            AlertUtils.showWarning("Chưa đăng nhập", "Vui lòng đăng nhập.");
            return;
        }
        if (UserFactory.toRole(currentUser) != Role.BIDDER) {
            AlertUtils.showError("Lỗi Quyền", "Chỉ tài khoản BIDDER mới dùng đấu giá tự động!");
            return;
        }

        RequestExecutor.send(
                new DisableAutoBidRequest(currentAuction.getId(), currentUser.getId()),
                this::handleDisableAutoBidResponse,
                error -> {
                    AlertUtils.showError("Tắt đấu giá tự động thất bại", error);
                    if (messageLabel != null) messageLabel.setText("");
                }
        );
        if (messageLabel != null) messageLabel.setText("Đang tắt đấu giá tự động...");
    }

    private void handleDisableAutoBidResponse(Object response) {
        if (response instanceof SetAutoBidResponse r) {
            AlertUtils.showInfo(r.success() ? "Thành công" : "Thông báo", r.message());
            if (messageLabel != null) messageLabel.setText(r.success() ? "Đã tắt đấu giá tự động." : "");
        } else {
            AlertUtils.showError("Lỗi",
                    "Phản hồi không hợp lệ: " + response.getClass().getSimpleName());
            if (messageLabel != null) messageLabel.setText("");
        }
    }

    public void onCancelAuctionClicked() {
        if (currentAuction == null) {
            AlertUtils.showWarning("Thông báo", "Không có phiên để hủy.");
            return;
        }
        User user = ClientSession.getCurrentUser();
        if (user == null || !user.canManage(currentAuction)) {
            AlertUtils.showError("Lỗi Quyền", "Bạn không có quyền hủy phiên này!");
            return;
        }

        boolean ok = AlertUtils.showConfirm("Xác nhận hủy phiên",
                "Bạn chắc chắn muốn hủy phiên này? Hành động không thể hoàn tác.");
        if (!ok) return;

        final String auctionId = currentAuction.getId();
        if (messageLabel != null) messageLabel.setText("Đang gửi yêu cầu hủy phiên...");

        RequestExecutor.send(
                new CancelAuctionRequest(auctionId),
                this::handleCancelResult,
                error -> {
                    AlertUtils.showError("Hủy phiên thất bại", error);
                    if (messageLabel != null) messageLabel.setText("");
                }
        );
    }

    private void handleCancelResult(Object response) {
        if (response instanceof CancelAuctionResult result) {
            switch (result) {
                case CancelAuctionResult.Success s ->
                        AlertUtils.showInfo("Thành công", "Đã hủy phiên đấu giá.");
                case CancelAuctionResult.Failure f -> {
                    AlertUtils.showError("Hủy phiên thất bại", f.reason());
                    if (messageLabel != null) messageLabel.setText("");
                }
            }
        } else {
            AlertUtils.showError("Lỗi",
                    "Phản hồi không hợp lệ từ server: " + response.getClass().getSimpleName());
            if (messageLabel != null) messageLabel.setText("");
        }
    }

    @Override
    public void onAuctionEvent(AuctionEvent event) {
        Auction updated = event.getAuction();

        if (currentAuctionId == null || !updated.getId().equals(currentAuctionId)) {
            return;
        }

        javafx.application.Platform.runLater(() -> {
            log.debug("[Detail] Nhận update auction: {} | endTime: {} | giá: {}",
                    updated.getId(), updated.getEndTime(), updated.getCurrentPrice());

            currentAuction = updated;
            expiredHandled = false;

            renderAuction(updated);
            renderBidHistoryChart(updated);
            startCountdown();

            if (event instanceof AuctionExtendedEvent ext) {
                if (messageLabel != null) messageLabel.setText(
                        "Phiên được gia hạn thêm " + ext.getExtendedSeconds()
                                + " giây do có người đấu giá phút chót!");
            } else if (event instanceof AuctionCancelledEvent cancelEvent) {
                Role byRole = cancelEvent.getCancelledByRole();
                String byText = (byRole != null) ? " bởi " + EnumFormatter.roleVi(byRole) : "";
                if (messageLabel != null) messageLabel.setText("⚠ Phiên đấu giá đã bị huỷ" + byText + ".");
                AlertUtils.showWarning("Phiên bị huỷ",
                        "Phiên đấu giá này đã bị huỷ" + byText + ". Bạn không thể đặt giá nữa.");
            } else if (event instanceof AuctionEndedEvent) {
                String winner = updated.getHighestBidderName();
                String msg = (winner != null && !winner.isBlank())
                        ? "Phiên đã kết thúc. Người thắng: " + winner
                        : "Phiên đã kết thúc. Không có người thắng.";
                if (messageLabel != null) messageLabel.setText(msg);
                AlertUtils.showInfo("Phiên kết thúc", msg);
            } else if (event instanceof AuctionPaidEvent) {
                if (messageLabel != null) messageLabel.setText("✓ Phiên đã thanh toán xong.");
            }
        });
    }

    private void handleLogout() {
        ClientSession.clear();
        SceneNavigator.switchScene("/fxml/Login.fxml");
    }

    @Override
    public void dispose() {
        AuctionEventBus.getInstance().removeObserver(this);
        stopCountdown();
    }

    private void renderBidHistoryChart(Auction auction) {
        if (bidHistoryChart == null) return;
        bidHistoryChart.getData().clear();

        List<Bid> bids = auction.getBidHistory();
        if (bids == null || bids.isEmpty()) return;

        // BidHistorySeriesBuilder.buildSeries() chỉ nhận List<Bid>.
        // Filter thủ công để tách manual / auto trước khi build.
        List<Bid> manualBids = bids.stream()
                .filter(b -> b.getSource() == BidSource.MANUAL)
                .toList();
        List<Bid> autoBids = bids.stream()
                .filter(b -> b.getSource() == BidSource.AUTO)
                .toList();

        if (!manualBids.isEmpty()) {
            XYChart.Series<String, Number> manualSeries =
                    BidHistorySeriesBuilder.buildSeries(manualBids);
            bidHistoryChart.getData().add(manualSeries);
            applySeriesColor(manualSeries, MANUAL_COLOR);
        }
        if (!autoBids.isEmpty()) {
            XYChart.Series<String, Number> autoSeries =
                    BidHistorySeriesBuilder.buildSeries(autoBids);
            bidHistoryChart.getData().add(autoSeries);
            applySeriesColor(autoSeries, AUTO_COLOR);
        }
    }

    private void applySeriesColor(XYChart.Series<String, Number> series, String color) {
        Node line = series.getNode().lookup(".chart-series-line");
        if (line != null) {
            line.setStyle("-fx-stroke: " + color + "; -fx-stroke-width: 2px;");
        }
        for (XYChart.Data<String, Number> data : series.getData()) {
            Node symbol = data.getNode();
            if (symbol != null) {
                symbol.setStyle("-fx-background-color: " + color + ", white;");
                Tooltip.install(symbol, new Tooltip(
                        data.getXValue() + "\n" + MoneyFormatter.formatVnd(data.getYValue().longValue())));
            }
        }
    }
}