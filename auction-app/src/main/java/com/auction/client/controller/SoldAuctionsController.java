package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.network.ServerConnection;
import com.auction.client.realtime.AuctionEventBus;
import com.auction.client.realtime.AuctionEventObserver;
import com.auction.client.util.*;
import com.auction.client.util.SidebarBuilder.NavKey;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;
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

public class SoldAuctionsController implements AuctionEventObserver, Disposable {

    @FXML private StackPane topbarContainer;
    @FXML private Label summaryLabel;
    @FXML private TableView<Auction> soldTable;
    @FXML private TableColumn<Auction, String> colLot;
    @FXML private TableColumn<Auction, String> colPrice;
    @FXML private TableColumn<Auction, String> colSoldAt;
    @FXML private TableColumn<Auction, String> colStatus;

    private final List<Auction> allAuctions = new ArrayList<>();

    @FXML
    public void initialize() {
        if (topbarContainer != null && ClientSession.getCurrentUser() != null) {
            var topbar = TopbarBuilder.build(
                    ClientSession.getCurrentUser(),
                    NavKey.SELLER_SOLD,
                    this::handleNavClick,
                    this::handleLogout
            );
            topbarContainer.getChildren().add(topbar);
        }

        if (colLot != null) {
            colLot.setCellValueFactory(c ->
                    new SimpleStringProperty(
                            c.getValue().getItemName() != null && !c.getValue().getItemName().isBlank()
                                    ? c.getValue().getItemName()
                                    : "Mã phiên " + shortId(c.getValue().getId())));
        }
        if (colPrice != null) {
            colPrice.setCellValueFactory(c ->
                    new SimpleStringProperty(MoneyFormatter.formatVnd(c.getValue().getCurrentPrice())));
        }
        if (colSoldAt != null) {
            colSoldAt.setCellValueFactory(c -> {
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

        List<Auction> sold = new ArrayList<>();
        long totalRevenue = 0;

        for (Auction a : allAuctions) {
            // "Đã bán" = phiên của mình ĐÃ có người thắng
            if (sellerId.equals(a.getSellerId()) && a.getStatus() == AuctionStatus.PAID) {                sold.add(a);
                totalRevenue += a.getCurrentPrice();
            }
        }

        sold.sort(Comparator.comparing(
                (Auction a) -> a.getEndTime() == null ? java.time.LocalDateTime.MIN : a.getEndTime()
        ).reversed());

        if (soldTable != null) {
            soldTable.getItems().setAll(sold);
        }

        if (summaryLabel != null) {
            if (sold.isEmpty()) {
                summaryLabel.setText("Bạn chưa bán được phiên nào.");
            } else {
                summaryLabel.setText("Đã bán " + sold.size() + " phiên · Tổng doanh thu: "
                        + MoneyFormatter.formatVnd(totalRevenue));
            }
        }
    }

    private void handleNavClick(NavKey key) {
        if (key == NavKey.SELLER_SOLD) return; // đang ở đây
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
