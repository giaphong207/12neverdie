package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.network.ServerConnection;
import com.auction.client.realtime.AuctionEventBus;
import com.auction.client.realtime.AuctionEventObserver;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.AuctionCardBuilder;
import com.auction.client.util.MoneyFormatter;
import com.auction.client.util.SceneNavigator;
import com.auction.client.util.SidebarBuilder.NavKey;
import com.auction.client.util.TopbarBuilder;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;
import com.auction.shared.networkMessage.AuctionEvents.*;
import com.auction.shared.networkMessage.Requests.*;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SellerAuctionsController implements AuctionEventObserver {

    @FXML private StackPane topbarContainer;
    @FXML private HBox statCardsContainer;
    @FXML private FlowPane auctionGrid;
    @FXML private Label summaryLabel;

    private final List<Auction> allAuctions = new ArrayList<>();

    @FXML
    public void initialize() {
        if (topbarContainer != null && ClientSession.getCurrentUser() != null) {
            var topbar = TopbarBuilder.build(
                    ClientSession.getCurrentUser(),
                    NavKey.SELLER_AUCTIONS,
                    this::handleNavClick,
                    this::handleLogout
            );
            topbarContainer.getChildren().add(topbar);
        }

        AuctionEventBus.getInstance().addObserver(this);
        try {
            ServerConnection.getInstance().send(new SubscribeAuctionListRequest());
        } catch (IOException e) {
            AlertUtils.showError("Lỗi kết nối", "Không tải được dữ liệu: " + e.getMessage());
        }
    }

    @Override
    public void onAuctionEvent(AuctionEvent event) {
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
        String sellerId = user.getId();

        List<Auction> mine = new ArrayList<>();
        long running = 0;
        long finished = 0;
        long revenue = 0;

        for (Auction a : allAuctions) {
            if (!sellerId.equals(a.getSellerId())) continue;
            mine.add(a);

            switch (a.getStatus()) {
                case RUNNING -> running++;
                case FINISHED, PAID -> {
                    finished++;
                    revenue += a.getCurrentPrice();
                }
                default -> {}
            }
        }

        renderStats(mine.size(), running, finished, revenue);

        mine.sort(Comparator.comparing(
                (Auction a) -> a.getStatus() != AuctionStatus.RUNNING
        ).thenComparing(
                a -> a.getEndTime() == null ? java.time.LocalDateTime.MAX : a.getEndTime()
        ));

        auctionGrid.getChildren().clear();
        if (mine.isEmpty()) {
            Label empty = new Label("Bạn chưa có phiên đấu giá nào. Vào 'Sản phẩm của tôi' để tạo sản phẩm mới.");
            empty.getStyleClass().add("text-tertiary");
            auctionGrid.getChildren().add(empty);
        } else {
            for (Auction a : mine) {
                var card = AuctionCardBuilder.build(a, this::openAuctionDetail);
                auctionGrid.getChildren().add(card);
            }
        }

        if (summaryLabel != null) {
            summaryLabel.setText("Tổng " + mine.size() + " phiên đấu giá");
        }
    }

    private void renderStats(long total, long running, long finished, long revenue) {
        if (statCardsContainer == null) return;
        statCardsContainer.getChildren().clear();

        var c1 = com.auction.client.util.StatCardBuilder.build("Tổng phiên", String.valueOf(total));
        var c2 = com.auction.client.util.StatCardBuilder.build("Đang chạy", String.valueOf(running));
        var c3 = com.auction.client.util.StatCardBuilder.build("Đã kết thúc", String.valueOf(finished));
        var c4 = com.auction.client.util.StatCardBuilder.build("Doanh thu", MoneyFormatter.formatVnd(revenue));

        HBox.setHgrow(c1, Priority.ALWAYS);
        HBox.setHgrow(c2, Priority.ALWAYS);
        HBox.setHgrow(c3, Priority.ALWAYS);
        HBox.setHgrow(c4, Priority.ALWAYS);

        statCardsContainer.getChildren().addAll(c1, c2, c3, c4);
    }

    private void openAuctionDetail(Auction auction) {
        ClientSession.setSelectedAuctionId(auction.getId());
        SceneNavigator.switchScene("/fxml/AuctionDetail.fxml");
    }

    private void handleNavClick(NavKey key) {
        switch (key) {
            case SELLER_OVERVIEW -> SceneNavigator.switchScene("/fxml/SellerDashboard.fxml");
            case SELLER_PRODUCTS -> SceneNavigator.switchScene("/fxml/ProductManagement.fxml");
            case SELLER_AUCTIONS -> { /* đang ở đây */ }
            default -> AlertUtils.showInfo("Sắp ra mắt", "Tính năng này đang được phát triển.");
        }
    }

    private void handleLogout() {
        AuctionEventBus.getInstance().removeObserver(this);
        ClientSession.clear();
        SceneNavigator.switchScene("/fxml/Login.fxml");
    }
}