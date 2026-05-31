package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.network.ServerConnection;
import com.auction.client.realtime.AuctionEventBus;
import com.auction.client.realtime.AuctionEventObserver;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.AuctionCardBuilder;
import com.auction.client.util.NavRouter;
import com.auction.client.util.SceneNavigator;
import com.auction.client.util.SidebarBuilder.NavKey;
import com.auction.client.util.TopbarBuilder;
import com.auction.client.util.StatCardBuilder;
import com.auction.client.util.Disposable;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;
import com.auction.shared.model.bid.Bid;
import com.auction.shared.networkMessage.AuctionEvents.*;
import com.auction.shared.networkMessage.Requests.*;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BidderDashboardController implements AuctionEventObserver, Disposable {

    @FXML private StackPane topbarContainer;
    @FXML private Label welcomeLabel;
    @FXML private HBox statCardsContainer;
    @FXML private FlowPane featuredAuctionsContainer;
    @FXML private VBox endingSoonContainer;

    private final List<Auction> allAuctions = new ArrayList<>();

    @FXML
    public void initialize() {
        // Build sidebar
        if (topbarContainer != null && ClientSession.getCurrentUser() != null) {
            var user = ClientSession.getCurrentUser();
            var topbar = TopbarBuilder.build(
                    user,
                    NavKey.BIDDER_HOME,
                    this::handleNavClick,
                    this::handleLogout
            );
            topbarContainer.getChildren().add(topbar);
        }

        // Welcome message
        var user = ClientSession.getCurrentUser();
        if (welcomeLabel != null && user != null) {
            welcomeLabel.setText("Chào mừng trở lại, " + user.getUsername());
        }

        // Render placeholder ngay (trước khi nhận data)
        renderStats(0, 0, 0);
        renderFeaturedAuctions(List.of());
        renderEndingSoon(List.of());

        // Subscribe để nhận data thật
        AuctionEventBus.getInstance().addObserver(this);
        try {
            ServerConnection.getInstance().send(new SubscribeAuctionListRequest());
        } catch (IOException e) {
            AlertUtils.showError("Lỗi kết nối", "Không tải được danh sách phiên: " + e.getMessage());
        }
    }

    @Override
    public void onAuctionEvent(AuctionEvent event) {
        Auction updated = event.getAuction();
        Platform.runLater(() -> {
            // Update hoặc add auction vào list local
            int idx = indexOfAuction(updated.getId());
            if (idx >= 0) {
                allAuctions.set(idx, updated);
            } else {
                allAuctions.add(updated);
            }
            recompute();
        });
    }

    private int indexOfAuction(String id) {
        for (int i = 0; i < allAuctions.size(); i++) {
            if (allAuctions.get(i).getId().equals(id)) return i;
        }
        return -1;
    }

    /** Tính lại KPI và render UI dựa trên allAuctions. */
    private void recompute() {
        var user = ClientSession.getCurrentUser();
        if (user == null) return;

        String userId = user.getId();
        long followingCount = 0;
        long wonCount = 0;
        long bidsToday = 0;
        LocalDate today = LocalDate.now();

        for (Auction a : allAuctions) {
            boolean userBidHere = false;
            if (a.getBidHistory() != null) {
                for (Bid b : a.getBidHistory()) {
                    if (userId.equals(b.getBidderId())) {
                        userBidHere = true;
                        if (b.getCreatedAt() != null
                                && b.getCreatedAt().toLocalDate().equals(today)) {
                            bidsToday++;
                        }
                    }
                }
            }
            if (userBidHere && a.getStatus() == AuctionStatus.RUNNING) {
                followingCount++;
            }
            if (userId.equals(a.getWinnerBidderId())) {
                wonCount++;
            }
        }

        renderStats(followingCount, wonCount, bidsToday);

        // Featured: 3 phiên RUNNING giá cao nhất
        List<Auction> featured = allAuctions.stream()
                .filter(a -> a.getStatus() == AuctionStatus.RUNNING)
                .sorted(Comparator.comparingLong(Auction::getCurrentPrice).reversed())
                .limit(3)
                .toList();
        renderFeaturedAuctions(featured);

        // Ending soon: 3 phiên kết thúc sớm nhất (còn dưới 2 giờ)
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime twoHoursLater = now.plusHours(2);
        List<Auction> endingSoon = allAuctions.stream()
                .filter(a -> a.getStatus() == AuctionStatus.RUNNING)
                .filter(a -> a.getEndTime() != null && a.getEndTime().isBefore(twoHoursLater))
                .sorted(Comparator.comparing(Auction::getEndTime))
                .limit(3)
                .toList();
        renderEndingSoon(endingSoon);
    }

    private void renderStats(long following, long won, long bidsToday) {
        if (statCardsContainer == null) return;
        statCardsContainer.getChildren().clear();
        var c1 = StatCardBuilder.build("Phiên đang theo dõi", String.valueOf(following));
        var c2 = StatCardBuilder.build("Phiên đã thắng", String.valueOf(won));
        var c3 = StatCardBuilder.build("Đặt giá hôm nay", String.valueOf(bidsToday));
        HBox.setHgrow(c1, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(c2, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(c3, javafx.scene.layout.Priority.ALWAYS);
        statCardsContainer.getChildren().addAll(c1, c2, c3);
    }

    private void renderFeaturedAuctions(List<Auction> auctions) {
        if (featuredAuctionsContainer == null) return;
        featuredAuctionsContainer.getChildren().clear();

        if (auctions.isEmpty()) {
            Label empty = new Label("Chưa có phiên đấu giá nổi bật nào.");
            empty.getStyleClass().add("text-tertiary");
            featuredAuctionsContainer.getChildren().add(empty);
            return;
        }

        for (Auction a : auctions) {
            var card = AuctionCardBuilder.build(a, this::openAuctionDetail);
            featuredAuctionsContainer.getChildren().add(card);
        }
    }

    private void renderEndingSoon(List<Auction> auctions) {
        if (endingSoonContainer == null) return;
        endingSoonContainer.getChildren().clear();

        if (auctions.isEmpty()) {
            Label empty = new Label("Không có phiên nào kết thúc trong 2 giờ tới.");
            empty.getStyleClass().add("text-tertiary");
            endingSoonContainer.getChildren().add(empty);
            return;
        }

        for (Auction a : auctions) {
            HBox row = buildEndingSoonRow(a);
            endingSoonContainer.getChildren().add(row);
        }
    }

    private HBox buildEndingSoonRow(Auction auction) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("card");
        row.setStyle(row.getStyle() + "-fx-padding: 16 20 16 20;");

        String title = auction.getItemName() != null && !auction.getItemName().isBlank()
                ? auction.getItemName()
                : "Mã phiên " + shortId(auction.getId());
        Label lot = new Label(title);
        lot.getStyleClass().add("label-tiny-uppercase");

        Label price = new Label(com.auction.client.util.MoneyFormatter.formatVnd(auction.getCurrentPrice()));
        price.getStyleClass().add("price-medium");
        price.setStyle("-fx-text-fill: -fx-champagne;");

        var spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        var remaining = java.time.Duration.between(java.time.LocalDateTime.now(), auction.getEndTime());
        Label countdown = new Label("⏱  " + com.auction.client.util.CountdownUtil.formatRemaining(remaining));
        countdown.setStyle("-fx-text-fill: -fx-bordeaux; -fx-font-weight: 600; -fx-font-size: 14px;");

        javafx.scene.control.Button viewBtn = new javafx.scene.control.Button("Xem →");
        viewBtn.getStyleClass().add("btn-link");
        viewBtn.setOnAction(e -> openAuctionDetail(auction));

        row.getChildren().addAll(lot, price, spacer, countdown, viewBtn);
        return row;
    }

    private void openAuctionDetail(Auction auction) {
        ClientSession.setSelectedAuctionId(auction.getId());
        SceneNavigator.switchScene("/fxml/AuctionDetail.fxml");
    }

    private void handleNavClick(NavKey key) {
        if (key == NavKey.BIDDER_HOME) return; // đang ở đây
        NavRouter.route(key);
    }

    private void handleLogout() {
        ClientSession.clear();
        SceneNavigator.switchScene("/fxml/Login.fxml");
    }
    @Override
    public void dispose() {
        AuctionEventBus.getInstance().removeObserver(this);
    }

    private String shortId(String id) {
        if (id == null) return "---";
        return id.length() > 6 ? id.substring(0, 6).toUpperCase() : id.toUpperCase();
    }
}