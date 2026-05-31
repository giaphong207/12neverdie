package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.Disposable;
import com.auction.client.util.EnumFormatter;
import com.auction.client.util.NavRouter;
import com.auction.client.util.RequestExecutor;
import com.auction.client.util.SceneNavigator;
import com.auction.client.util.SidebarBuilder.NavKey;
import com.auction.client.util.StatCardBuilder;
import com.auction.client.util.TopbarBuilder;
import com.auction.shared.model.user.Role;
import com.auction.shared.networkMessage.Requests.GetAllUsersRequest;
import com.auction.shared.networkMessage.Results.GetAllUsersResult;
import com.auction.shared.networkMessage.Results.UserRow;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

import java.util.Locale;

/**
 * Trang "Người dùng" cho quản trị viên: danh sách đầy đủ tài khoản,
 * tìm kiếm theo tên, và thống kê nhanh theo vai trò.
 */
public class AdminUsersController implements Disposable {

    @FXML private StackPane topbarContainer;
    @FXML private Label summaryLabel;
    @FXML private HBox statCardsContainer;
    @FXML private TableView<UserRow> usersTable;
    @FXML private TableColumn<UserRow, String> colUserName;
    @FXML private TableColumn<UserRow, String> colUserRole;
    @FXML private TextField searchField;

    private final ObservableList<UserRow> allUsers = FXCollections.observableArrayList();
    private FilteredList<UserRow> filtered;

    @FXML
    public void initialize() {
        if (topbarContainer != null && ClientSession.getCurrentUser() != null) {
            var topbar = TopbarBuilder.build(
                    ClientSession.getCurrentUser(),
                    NavKey.ADMIN_USERS,
                    this::handleNavClick,
                    this::handleLogout
            );
            topbarContainer.getChildren().add(topbar);
        }

        setupColumns();

        filtered = new FilteredList<>(allUsers, u -> true);
        if (usersTable != null) usersTable.setItems(filtered);

        if (searchField != null) {
            searchField.textProperty().addListener((obs, old, text) -> applyFilter(text));
        }

        renderStats(0, 0, 0);
        loadUsers();
    }

    private void setupColumns() {
        if (colUserName != null) {
            colUserName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().username()));
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
                    new SimpleStringProperty(EnumFormatter.roleVi(c.getValue().role())));
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
    }

    private void applyFilter(String text) {
        if (filtered == null) return;
        String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        filtered.setPredicate(u -> q.isEmpty()
                || (u.username() != null && u.username().toLowerCase(Locale.ROOT).contains(q)));
        if (summaryLabel != null && !q.isEmpty()) {
            summaryLabel.setText("Tìm thấy " + filtered.size() + " / " + allUsers.size() + " tài khoản");
        } else if (summaryLabel != null) {
            summaryLabel.setText("Tổng " + allUsers.size() + " tài khoản");
        }
    }

    private void loadUsers() {
        RequestExecutor.send(
                new GetAllUsersRequest(),
                response -> {
                    if (response instanceof GetAllUsersResult result) {
                        switch (result) {
                            case GetAllUsersResult.Success s -> {
                                allUsers.setAll(s.users());
                                long sellers = s.users().stream().filter(u -> u.role() == Role.SELLER).count();
                                long bidders = s.users().stream().filter(u -> u.role() == Role.BIDDER).count();
                                renderStats(s.users().size(), sellers, bidders);
                                applyFilter(searchField == null ? "" : searchField.getText());
                            }
                            case GetAllUsersResult.Failure f ->
                                    AlertUtils.showError("Lỗi", "Không tải được người dùng: " + f.reason());
                        }
                    }
                },
                error -> AlertUtils.showError("Lỗi mạng", "Không tải được người dùng: " + error)
        );
    }

    private void renderStats(long total, long sellers, long bidders) {
        if (statCardsContainer == null) return;
        statCardsContainer.getChildren().clear();

        var c1 = StatCardBuilder.build("Tổng người dùng", String.valueOf(total), "Tài khoản");
        var c2 = StatCardBuilder.build("Người bán", String.valueOf(sellers), "Seller");
        var c3 = StatCardBuilder.build("Người đấu giá", String.valueOf(bidders), "Bidder");

        HBox.setHgrow(c1, Priority.ALWAYS);
        HBox.setHgrow(c2, Priority.ALWAYS);
        HBox.setHgrow(c3, Priority.ALWAYS);

        statCardsContainer.getChildren().addAll(c1, c2, c3);
    }

    private void handleNavClick(NavKey key) {
        if (key == NavKey.ADMIN_USERS) return; // đang ở đây
        NavRouter.route(key);
    }

    private void handleLogout() {
        ClientSession.clear();
        SceneNavigator.switchScene("/fxml/Login.fxml");
    }

    @Override
    public void dispose() {
        // Không subscribe event nào nên không cần dọn dẹp đặc biệt.
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
}
