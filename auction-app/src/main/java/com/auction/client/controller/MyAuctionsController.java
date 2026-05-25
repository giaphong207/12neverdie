package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.network.ServerConnection;
import com.auction.client.realtime.AuctionEventBus;
import com.auction.client.realtime.AuctionEventObserver;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.AuctionCardBuilder;
import com.auction.client.util.SceneNavigator;
import com.auction.client.util.SidebarBuilder;
import com.auction.client.util.SidebarBuilder.NavKey;
import com.auction.shared.model.Auction;
import com.auction.shared.model.Bid;
import com.auction.shared.networkMessage.event.AuctionEvent;
import com.auction.shared.networkMessage.request.SubscribeAuctionListRequest;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MyAuctionsController implements AuctionEventObserver {

    @FXML private StackPane sidebarContainer;
    @FXML private TabPane tabPane;
    @FXML private FlowPane participatingGrid;
    @FXML private FlowPane wonGrid;
    @FXML private FlowPane lostGrid;
    @FXML private Label countParticipating;
    @FXML private Label countWon;
    @FXML private Label countLost;

    private final List<Auction> allAuctions = new ArrayList<>();

    @FXML
    public void initialize() {
        if (sidebarContainer != null && ClientSession.getCurrentUser() != null) {
            var sidebar = SidebarBuilder.build(
                    ClientSession.getCurrentUser(),
                    NavKey.BIDDER_MINE,
                    this::handleNavClick,
                    this::handleLogout
            );
            sidebarContainer.getChildren().add(sidebar);
        }

        AuctionEventBus.getInstance().addObserver(this);
        try {
            ServerConnection.getInstance().send(new SubscribeAuctionListRequest());
        } catch (IOException e) {
            AlertUtils.showError("Lỗi kết nối", "Không tải được dữ liệu: " + e.getMessage());
        }
    }

    @Override
    public void onAuctionUpdated(AuctionEvent event) {
        Auction updated = event.getAuction();
        Platform.runLater(() -> {
            int idx = indexOfAuction(updated.getId());
            if (idx >= 0) allAuctions.set(idx, updated);
            else allAuctions.add(updated);
            recompute();
        });
    }

    private int indexOfAuction(String id) {
        for (int i = 0; i < allAuctions.size(); i++) {
            if (allAuctions.get(i).getId().equals(id)) return i;
        }
        return -1;
    }

    private void recompute() {
        var user = ClientSession.getCurrentUser();
        if (user == null) return;
        String userId = user.getId();

        List<Auction> participating = new ArrayList<>();
        List<Auction> won = new ArrayList<>();
        List<Auction> lost = new ArrayList<>();

        for (Auction a : allAuctions) {
            boolean iBid = userHasBid(a, userId);
            if (!iBid) continue;

            switch (a.getStatus()) {
                case RUNNING, OPEN -> participating.add(a);
                case FINISHED, PAID -> {
                    if (userId.equals(a.getWinnerBidderId())) won.add(a);
                    else lost.add(a);
                }
                case CANCELED -> lost.add(a);
            }
        }

        // Sort: theo endTime giảm dần
        Comparator<Auction> byEndDesc = Comparator.comparing(
                (Auction a) -> a.getEndTime() == null ? java.time.LocalDateTime.MIN : a.getEndTime()
        ).reversed();
        participating.sort(byEndDesc);
        won.sort(byEndDesc);
        lost.sort(byEndDesc);

        renderTab(participatingGrid, participating, "Bạn chưa tham gia phiên đấu giá nào.");
        renderTab(wonGrid, won, "Bạn chưa thắng phiên nào.");
        renderTab(lostGrid, lost, "Bạn chưa có phiên đã thua.");

        countParticipating.setText(String.valueOf(participating.size()));
        countWon.setText(String.valueOf(won.size()));
        countLost.setText(String.valueOf(lost.size()));
    }

    private boolean userHasBid(Auction a, String userId) {
        if (a.getBidHistory() == null) return false;
        for (Bid b : a.getBidHistory()) {
            if (userId.equals(b.getBidderId())) return true;
        }
        return false;
    }

    private void renderTab(FlowPane grid, List<Auction> auctions, String emptyMessage) {
        if (grid == null) return;
        grid.getChildren().clear();

        if (auctions.isEmpty()) {
            VBox empty = new VBox(8);
            empty.setAlignment(javafx.geometry.Pos.CENTER);
            empty.setPrefWidth(grid.getPrefWidth() > 0 ? grid.getPrefWidth() : 800);
            empty.setPadding(new javafx.geometry.Insets(60, 20, 60, 20));
            Label emptyIcon = new Label("○");
            emptyIcon.setStyle("-fx-font-size: 36px; -fx-text-fill: -fx-text-tertiary;");
            Label emptyText = new Label(emptyMessage);
            emptyText.getStyleClass().add("text-secondary");
            empty.getChildren().addAll(emptyIcon, emptyText);
            grid.getChildren().add(empty);
            return;
        }

        for (Auction a : auctions) {
            var card = AuctionCardBuilder.build(a, this::openAuctionDetail);
            grid.getChildren().add(card);
        }
    }

    private void openAuctionDetail(Auction auction) {
        ClientSession.setSelectedAuctionId(auction.getId());
        SceneNavigator.switchScene("/fxml/AuctionDetail.fxml");
    }

    private void handleNavClick(NavKey key) {
        switch (key) {
            case BIDDER_HOME -> SceneNavigator.switchScene("/fxml/BidderDashboard.fxml");
            case BIDDER_LIVE -> SceneNavigator.switchScene("/fxml/AuctionList.fxml");
            case BIDDER_MINE -> { /* đang ở đây */ }
            case BIDDER_WON -> SceneNavigator.switchScene("/fxml/WonAuctions.fxml");
            default -> AlertUtils.showInfo("Sắp ra mắt", "Tính năng này đang được phát triển.");
        }
    }

    private void handleLogout() {
        AuctionEventBus.getInstance().removeObserver(this);
        ClientSession.clear();
        SceneNavigator.switchScene("/fxml/Login.fxml");
    }
}