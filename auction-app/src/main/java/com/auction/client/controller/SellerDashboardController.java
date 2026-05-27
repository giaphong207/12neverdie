package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.main.ClientApp;
import com.auction.client.network.ServerMessageListener;
import com.auction.client.network.ServerConnection;
import com.auction.client.realtime.AuctionEventBus;
import com.auction.client.realtime.AuctionEventObserver;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.EnumFormatter;
import com.auction.client.util.MoneyFormatter;
import com.auction.client.util.SceneNavigator;
import com.auction.client.util.SidebarBuilder;
import com.auction.client.util.SidebarBuilder.NavKey;
import com.auction.client.util.StatCardBuilder;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.item.Item;
import com.auction.shared.networkMessage.AuctionEvents.*;
import com.auction.shared.networkMessage.Requests.*;
import com.auction.shared.networkMessage.Results.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SellerDashboardController implements AuctionEventObserver {

    @FXML private StackPane sidebarContainer;
    @FXML private HBox statCardsContainer;
    @FXML private TableView<Auction> recentAuctionsTable;
    @FXML private TableColumn<Auction, String> colLot;
    @FXML private TableColumn<Auction, String> colCurrentPrice;
    @FXML private TableColumn<Auction, String> colStatus;
    @FXML private TableColumn<Auction, String> colEndTime;

    private final List<Auction> allAuctions = new ArrayList<>();
    private int sellerItemCount = 0;

    @FXML
    public void initialize() {
        if (sidebarContainer != null && ClientSession.getCurrentUser() != null) {
            var sidebar = SidebarBuilder.build(
                    ClientSession.getCurrentUser(),
                    NavKey.SELLER_OVERVIEW,
                    this::handleNavClick,
                    this::handleLogout
            );
            sidebarContainer.getChildren().add(sidebar);
        }

        // Setup table columns
        if (colLot != null) {
            colLot.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(
                            "LOT №" + shortId(c.getValue().getId())));
        }
        if (colCurrentPrice != null) {
            colCurrentPrice.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(
                            MoneyFormatter.formatVnd(c.getValue().getCurrentPrice())));
        }
        if (colStatus != null) {
            colStatus.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(
                            EnumFormatter.auctionStatusVi(c.getValue().getStatus())));
        }
        if (colEndTime != null) {
            colEndTime.setCellValueFactory(c -> {
                var endTime = c.getValue().getEndTime();
                String text = endTime == null ? "—"
                        : endTime.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));
                return new javafx.beans.property.SimpleStringProperty(text);
            });
        }

        // Render placeholder
        renderStats(0, 0, 0, 0);

        // Load data thật
        loadSellerItemCount();
        AuctionEventBus.getInstance().addObserver(this);
        try {
            ServerConnection.getInstance().send(new SubscribeAuctionListRequest());
        } catch (IOException e) {
            AlertUtils.showError("Lỗi kết nối", "Không tải được phiên: " + e.getMessage());
        }
    }

    private void loadSellerItemCount() {
        var user = ClientSession.getCurrentUser();
        if (user == null) return;

        new Thread(() -> {
            try {
                ServerConnection.getInstance().send(new GetSellerItemsRequest(user.getId()));
                ServerMessageListener listener = ClientApp.getListener();
                Object response = listener.waitForResponse();

                Platform.runLater(() -> {
                    if (response instanceof GetSellerItemsResult result) {
                        switch (result) {
                            case GetSellerItemsResult.Success s -> {
                                List<Item> items = s.items();
                                sellerItemCount = items == null ? 0 : items.size();
                                recompute();
                            }
                            case GetSellerItemsResult.Failure f -> {
                                System.err.println("Không load được items: " + f.reason());
                            }
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
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
        String sellerId = user.getId();

        long running = 0;
        long finished = 0;
        long revenue = 0;

        List<Auction> myAuctions = new ArrayList<>();

        for (Auction a : allAuctions) {
            if (!sellerId.equals(a.getSellerId())) continue;
            myAuctions.add(a);

            switch (a.getStatus()) {
                case RUNNING -> running++;
                case FINISHED, PAID -> {
                    finished++;
                    revenue += a.getCurrentPrice();
                }
                default -> {}
            }
        }

        renderStats(sellerItemCount, running, revenue, finished);

        // Sắp xếp theo thời gian kết thúc gần nhất
        myAuctions.sort(Comparator.comparing(
                (Auction a) -> a.getEndTime() == null ? java.time.LocalDateTime.MAX : a.getEndTime()
        ).reversed());

        if (recentAuctionsTable != null) {
            recentAuctionsTable.getItems().setAll(myAuctions.stream().limit(10).toList());
        }
    }

    private void renderStats(long itemCount, long running, long revenue, long finished) {
        if (statCardsContainer == null) return;
        statCardsContainer.getChildren().clear();

        var c1 = StatCardBuilder.build("Số sản phẩm", String.valueOf(itemCount));
        var c2 = StatCardBuilder.build("Phiên đang chạy", String.valueOf(running));
        var c3 = StatCardBuilder.build("Tổng doanh thu", MoneyFormatter.formatVnd(revenue));
        var c4 = StatCardBuilder.build("Phiên đã kết thúc", String.valueOf(finished));

        HBox.setHgrow(c1, Priority.ALWAYS);
        HBox.setHgrow(c2, Priority.ALWAYS);
        HBox.setHgrow(c3, Priority.ALWAYS);
        HBox.setHgrow(c4, Priority.ALWAYS);

        statCardsContainer.getChildren().addAll(c1, c2, c3, c4);
    }

    private void handleNavClick(NavKey key) {
        switch (key) {
            case SELLER_OVERVIEW -> { /* đang ở đây */ }
            case SELLER_PRODUCTS -> SceneNavigator.switchScene("/fxml/ProductManagement.fxml");
            case SELLER_AUCTIONS -> SceneNavigator.switchScene("/fxml/SellerAuctions.fxml");
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