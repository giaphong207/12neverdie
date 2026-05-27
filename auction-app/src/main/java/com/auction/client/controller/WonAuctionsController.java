package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.network.ServerConnection;
import com.auction.client.realtime.AuctionEventBus;
import com.auction.client.realtime.AuctionEventObserver;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.EnumFormatter;
import com.auction.client.util.MoneyFormatter;
import com.auction.client.util.SceneNavigator;
import com.auction.client.util.SidebarBuilder.NavKey;
import com.auction.client.util.TopbarBuilder;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.networkMessage.AuctionEvents.*;
import com.auction.shared.networkMessage.Requests.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class WonAuctionsController implements AuctionEventObserver {

    @FXML private StackPane topbarContainer;
    @FXML private Label summaryLabel;
    @FXML private TableView<Auction> wonTable;
    @FXML private TableColumn<Auction, String> colLot;
    @FXML private TableColumn<Auction, String> colPrice;
    @FXML private TableColumn<Auction, String> colWonAt;
    @FXML private TableColumn<Auction, String> colStatus;

    private final List<Auction> allAuctions = new ArrayList<>();

    @FXML
    public void initialize() {
        if (topbarContainer != null && ClientSession.getCurrentUser() != null) {
            var topbar = TopbarBuilder.build(
                    ClientSession.getCurrentUser(),
                    NavKey.BIDDER_WON,
                    this::handleNavClick,
                    this::handleLogout
            );
            topbarContainer.getChildren().add(topbar);
        }

        // Setup columns
        if (colLot != null) {
            colLot.setCellValueFactory(c ->
                    new SimpleStringProperty("LOT №" + shortId(c.getValue().getId())));
        }
        if (colPrice != null) {
            colPrice.setCellValueFactory(c ->
                    new SimpleStringProperty(MoneyFormatter.formatVnd(c.getValue().getCurrentPrice())));
        }
        if (colWonAt != null) {
            colWonAt.setCellValueFactory(c -> {
                var endTime = c.getValue().getEndTime();
                String text = endTime == null ? "—"
                        : endTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
                return new SimpleStringProperty(text);
            });
        }
        if (colStatus != null) {
            colStatus.setCellValueFactory(c ->
                    new SimpleStringProperty(EnumFormatter.auctionStatusVi(c.getValue().getStatus())));
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

        List<Auction> won = new ArrayList<>();
        long totalSpent = 0;

        for (Auction a : allAuctions) {
            if (userId.equals(a.getWinnerBidderId())) {
                won.add(a);
                totalSpent += a.getCurrentPrice();
            }
        }

        won.sort(Comparator.comparing(
                (Auction a) -> a.getEndTime() == null ? java.time.LocalDateTime.MIN : a.getEndTime()
        ).reversed());

        if (wonTable != null) {
            wonTable.getItems().setAll(won);
        }

        if (summaryLabel != null) {
            if (won.isEmpty()) {
                summaryLabel.setText("Bạn chưa thắng phiên đấu giá nào.");
            } else {
                summaryLabel.setText("Đã thắng " + won.size() + " phiên · Tổng chi tiêu: "
                        + MoneyFormatter.formatVnd(totalSpent));
            }
        }
    }

    private void handleNavClick(NavKey key) {
        switch (key) {
            case BIDDER_HOME -> SceneNavigator.switchScene("/fxml/BidderDashboard.fxml");
            case BIDDER_LIVE -> SceneNavigator.switchScene("/fxml/AuctionList.fxml");
            case BIDDER_MINE -> SceneNavigator.switchScene("/fxml/MyAuctions.fxml");
            case BIDDER_WON -> { /* đang ở đây */ }
            default -> AlertUtils.showInfo("Sắp ra mắt", "Tính năng này đang được phát triển.");
        }
    }

    private void handleLogout() {
        AuctionEventBus.getInstance().removeObserver(this);
        ClientSession.clear();
        SceneNavigator.switchScene("/fxml/Login.fxml");
    }

    private String shortId(String id) {
        if (id == null) return "---";
        return id.length() > 6 ? id.substring(0, 6).toUpperCase() : id.toUpperCase();
    }
}