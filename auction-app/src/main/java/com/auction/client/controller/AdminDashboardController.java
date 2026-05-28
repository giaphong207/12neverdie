package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.network.ServerConnection;
import com.auction.client.realtime.AuctionEventBus;
import com.auction.client.realtime.AuctionEventObserver;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.MoneyFormatter;
import com.auction.client.util.SceneNavigator;
import com.auction.client.util.SidebarBuilder.NavKey;
import com.auction.client.util.TopbarBuilder;
import com.auction.client.util.StatCardBuilder;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.networkMessage.AuctionEvents.*;
import com.auction.shared.networkMessage.Requests.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AdminDashboardController implements AuctionEventObserver {

    @FXML private StackPane topbarContainer;
    @FXML private HBox statCardsContainer;
    @FXML private TableView<MockUser> usersTable;
    @FXML private TableColumn<MockUser, String> colUserName;
    @FXML private TableColumn<MockUser, String> colUserRole;
    @FXML private TableColumn<MockUser, String> colUserActivity;
    @FXML private TableColumn<MockUser, String> colUserStatus;

    private final List<Auction> allAuctions = new ArrayList<>();

    @FXML
    public void initialize() {
        if (topbarContainer != null && ClientSession.getCurrentUser() != null) {
            var topbar = TopbarBuilder.build(
                    ClientSession.getCurrentUser(),
                    NavKey.ADMIN_OVERVIEW,
                    this::handleNavClick,
                    this::handleLogout
            );
            topbarContainer.getChildren().add(topbar);
        }

        // Setup user table columns
        if (colUserName != null) {
            colUserName.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(c.getValue().username));
        }
        if (colUserRole != null) {
            colUserRole.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(c.getValue().role));
        }
        if (colUserActivity != null) {
            colUserActivity.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(c.getValue().lastActive));
        }
        if (colUserStatus != null) {
            colUserStatus.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(c.getValue().status));
        }

        renderStats(0, 0, 0, 0); //reset số liệu thống kê
        loadMockUsers(); //load dữ liệu mẫu

        // Vẫn subscribe để có số liệu phiên thật
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
        long running = 0;
        long totalRevenue = 0;
        for (Auction a : allAuctions) {
            switch (a.getStatus()) {
                case RUNNING -> running++;
                case FINISHED, PAID -> totalRevenue += a.getCurrentPrice();
                default -> {}
            }
        }
        // Tổng người dùng và pending: mock vì server chưa có endpoint
        renderStats(1240, running, totalRevenue, 8);
    }

    private void renderStats(long totalUsers, long running, long revenue, long pending) {
        if (statCardsContainer == null) return;
        statCardsContainer.getChildren().clear();

        var c1 = StatCardBuilder.build("Tổng người dùng", String.valueOf(totalUsers), "+4,2% so với tuần trước");
        var c2 = StatCardBuilder.build("Phiên đang diễn ra", String.valueOf(running), "+1,8% so với hôm qua");
        var c3 = StatCardBuilder.build("Tổng doanh thu", MoneyFormatter.formatVnd(revenue), "Kỳ hiện tại");
        var c4 = StatCardBuilder.build("Chờ duyệt", String.valueOf(pending), "Cần xử lý");

        HBox.setHgrow(c1, Priority.ALWAYS);
        HBox.setHgrow(c2, Priority.ALWAYS);
        HBox.setHgrow(c3, Priority.ALWAYS);
        HBox.setHgrow(c4, Priority.ALWAYS);

        statCardsContainer.getChildren().addAll(c1, c2, c3, c4);
    }

    private void loadMockUsers() {
        if (usersTable == null) return;
        usersTable.getItems().setAll(
                new MockUser("nguyenvana", "Người đấu giá", "2 phút trước", "HOẠT ĐỘNG"),
                new MockUser("tranthib", "Người bán", "1 giờ trước", "HOẠT ĐỘNG"),
                new MockUser("lehoangc", "Người đấu giá", "3 giờ trước", "HOẠT ĐỘNG"),
                new MockUser("phamthid", "Người bán", "1 ngày trước", "ĐÃ KHÓA"),
                new MockUser("buihuye", "Người đấu giá", "3 ngày trước", "HOẠT ĐỘNG")
        );
    }

    private void handleNavClick(NavKey key) {
        switch (key) {
            case ADMIN_OVERVIEW -> { /* đang ở đây */ }
            case ADMIN_AUCTIONS -> SceneNavigator.switchScene("/fxml/AuctionList.fxml");
            default -> AlertUtils.showInfo("Sắp ra mắt", "Tính năng này đang được phát triển.");
        }
    }

    private void handleLogout() {
        AuctionEventBus.getInstance().removeObserver(this);
        ClientSession.clear();
        SceneNavigator.switchScene("/fxml/Login.fxml");
    }

    /** Mock data type — chỉ để hiển thị trong table demo. */
    public static class MockUser {
        public final String username;
        public final String role;
        public final String lastActive;
        public final String status;

        public MockUser(String username, String role, String lastActive, String status) {
            this.username = username;
            this.role = role;
            this.lastActive = lastActive;
            this.status = status;
        }
    }
}