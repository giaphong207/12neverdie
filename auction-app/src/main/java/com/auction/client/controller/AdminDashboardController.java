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
import com.auction.client.util.Disposable;
import com.auction.client.util.RequestExecutor;
import com.auction.client.util.EnumFormatter;
import com.auction.shared.networkMessage.Results.*;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.networkMessage.AuctionEvents.*;
import com.auction.shared.networkMessage.Requests.*;
import com.auction.shared.networkMessage.Results.*;
import com.auction.shared.model.user.Role;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AdminDashboardController implements AuctionEventObserver, Disposable {

    @FXML private StackPane topbarContainer;
    @FXML private HBox statCardsContainer;
    @FXML private TableView<UserRow> usersTable;
    @FXML private TableColumn<UserRow, String> colUserName;
    @FXML private TableColumn<UserRow, String> colUserRole;

    private long totalUserCount = 0;
    private long sellerCount = 0;
    private long bidderCount = 0;
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
                    new javafx.beans.property.SimpleStringProperty(c.getValue().username()));
            // Avatar tròn chữ tắt + tên
            colUserName.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(String name, boolean empty) {
                    super.updateItem(name, empty);
                    if (empty || name == null || getIndex() >= getTableView().getItems().size()) {
                        setGraphic(null);
                        return;
                    }
                    UserRow row = getTableView().getItems().get(getIndex());
                    Label avatar = new Label(initials(name));
                    avatar.getStyleClass().addAll("avatar-circle", avatarClass(row.role()));
                    Label nameLabel = new Label(name);
                    HBox box = new HBox(10, avatar, nameLabel);
                    box.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(box);
                }
            });
        }
        if (colUserRole != null) {
            colUserRole.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(
                            EnumFormatter.roleVi(c.getValue().role())));
            // Badge màu theo vai trò
            colUserRole.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(String roleText, boolean empty) {
                    super.updateItem(roleText, empty);
                    if (empty || roleText == null || getIndex() >= getTableView().getItems().size()) {
                        setGraphic(null);
                        return;
                    }
                    UserRow row = getTableView().getItems().get(getIndex());
                    Label badge = new Label(roleText);
                    badge.getStyleClass().addAll("badge", roleBadgeClass(row.role()));
                    setGraphic(badge);
                }
            });
        }

        renderStats(0, 0, 0, 0); //reset số liệu thống kê
        loadUsers(); //load dữ liệu mẫu

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
        for (Auction a : allAuctions) {
            switch (a.getStatus()) {
                case RUNNING -> running++;
                default -> {}
            }
        }
        renderStats(totalUserCount, sellerCount, bidderCount, running);
    }

    private void renderStats(long totalUsers, long sellers, long bidders, long running) {
        if (statCardsContainer == null) return;
        statCardsContainer.getChildren().clear();

        var c1 = StatCardBuilder.build("Tổng người dùng", String.valueOf(totalUsers), "Hiện có");
        var c2 = StatCardBuilder.build("Người bán", String.valueOf(sellers), "Seller");
        var c3 = StatCardBuilder.build("Người đấu giá", String.valueOf(bidders), "Bidder");
        var c4 = StatCardBuilder.build("Phiên đang hoạt động", String.valueOf(running), "Đang mở");

        HBox.setHgrow(c1, Priority.ALWAYS);
        HBox.setHgrow(c2, Priority.ALWAYS);
        HBox.setHgrow(c3, Priority.ALWAYS);
        HBox.setHgrow(c4, Priority.ALWAYS);

        statCardsContainer.getChildren().addAll(c1, c2, c3, c4);
    }

    private void loadUsers() {
        if (usersTable == null) return;
        RequestExecutor.send(
                new GetAllUsersRequest(),
                response -> {
                    if (response instanceof GetAllUsersResult result) {
                        switch (result) {
                            case GetAllUsersResult.Success s -> {
                                usersTable.getItems().setAll(s.users());
                                totalUserCount = s.users().size();
                                sellerCount = s.users().stream().filter(u -> u.role() == Role.SELLER).count();
                                bidderCount = s.users().stream().filter(u -> u.role() == Role.BIDDER).count();
                                recompute();
                            }
                            case GetAllUsersResult.Failure f ->
                                    AlertUtils.showError("Lỗi", "Không tải được người dùng: " + f.reason());
                        }
                    }
                },
                error -> AlertUtils.showError("Lỗi mạng", "Không tải được người dùng: " + error)
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
        ClientSession.clear();
        SceneNavigator.switchScene("/fxml/Login.fxml");
    }

    /** Chữ tắt avatar: chữ cái đầu + cụm số cuối nếu có (bidder1→b1, admin→A). */
    private static String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String n = name.trim();
        char first = n.charAt(0);
        int i = n.length();
        while (i > 0 && Character.isDigit(n.charAt(i - 1))) i--;
        String digits = n.substring(i);
        return digits.isEmpty()
                ? String.valueOf(Character.toUpperCase(first))
                : Character.toLowerCase(first) + digits;
    }

    private static String avatarClass(Role role) {
        if (role == null) return "avatar-bidder";
        return switch (role) {
            case BIDDER -> "avatar-bidder";
            case SELLER -> "avatar-seller";
            case ADMIN -> "avatar-admin";
        };
    }

    private static String roleBadgeClass(Role role) {
        if (role == null) return "role-bidder";
        return switch (role) {
            case BIDDER -> "role-bidder";
            case SELLER -> "role-seller";
            case ADMIN -> "role-admin";
        };
    }

    @Override
    public void dispose() {
        AuctionEventBus.getInstance().removeObserver(this);
    }
}