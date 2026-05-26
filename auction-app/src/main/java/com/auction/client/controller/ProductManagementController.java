package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.main.ClientApp;
import com.auction.client.network.ServerMessageListener;
import com.auction.client.network.ServerConnection;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.SceneNavigator;
import com.auction.shared.factory.ItemFactory;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.item.ItemType;
import com.auction.shared.model.user.User;

import com.auction.shared.networkMessage.Requests.*;
import com.auction.shared.networkMessage.Responses.*;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.List;
import com.auction.client.util.EnumFormatter;
import com.auction.client.util.MoneyFormatter;
import com.auction.client.util.SidebarBuilder;
import com.auction.client.util.SidebarBuilder.NavKey;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

public class ProductManagementController {

    @FXML private TableView<Item> tblItems;
    @FXML private TableColumn<Item, String> colName;
    @FXML private TableColumn<Item, String> colDescription;
    @FXML private TableColumn<Item, String> colStartingPrice;
    @FXML private TableColumn<Item, String> colType;

    @FXML private TextField txtName;
    @FXML private TextArea txtDescription;
    @FXML private TextField txtStartingPrice;
    @FXML private ComboBox<ItemType> cbItemType;
    @FXML private StackPane sidebarContainer;

    private Item selectedItem;

    @FXML
    public void initialize() {
        // Build sidebar Seller
        if (sidebarContainer != null && ClientSession.getCurrentUser() != null) {
            var user = ClientSession.getCurrentUser();
            var sidebar = SidebarBuilder.build(
                    user,
                    NavKey.SELLER_PRODUCTS,
                    this::handleNavClick,
                    this::handleLogout
            );
            sidebarContainer.getChildren().add(sidebar);
        }

        // ComboBox ItemType hiển thị tiếng Việt
        cbItemType.setItems(FXCollections.observableArrayList(ItemType.values()));
        cbItemType.setConverter(new StringConverter<>() {
            @Override
            public String toString(ItemType type) {
                return EnumFormatter.itemTypeVi(type);
            }
            @Override
            public ItemType fromString(String s) {
                return null;
            }
        });

        // Setup table columns
        if (colName != null) {
            colName.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(c.getValue().getName()));
        }
        if (colDescription != null) {
            colDescription.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(c.getValue().getDescription()));
        }
        if (colStartingPrice != null) {
            colStartingPrice.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(
                            MoneyFormatter.formatVnd(c.getValue().getStartPrice())));
        }
        if (colType != null) {
            colType.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(
                            EnumFormatter.itemTypeVi(ItemFactory.toItemType(c.getValue()))));
        }

        tblItems.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldItem, newItem) -> {
                    selectedItem = newItem;
                    if (newItem != null) {
                        fillForm(newItem);
                    }
                });

        loadSellerProducts();
    }

    private void handleNavClick(NavKey key) {
        switch (key) {
            case SELLER_OVERVIEW -> SceneNavigator.switchScene("/fxml/SellerDashboard.fxml");
            case SELLER_PRODUCTS -> { /* đang ở đây */ }
            case SELLER_AUCTIONS -> SceneNavigator.switchScene("/fxml/SellerAuctions.fxml");

            default -> AlertUtils.showInfo("Sắp ra mắt", "Tính năng này đang được phát triển.");
        }
    }

    private void handleLogout() {
        ClientSession.clear();
        SceneNavigator.switchScene("/fxml/Login.fxml");
    }

    public void loadSellerProducts() {
        User currentUser = ClientSession.getCurrentUser();
        if (currentUser == null) {
            AlertUtils.showWarning("Chưa đăng nhập", "Cần đăng nhập để xem sản phẩm");
            return;
        }

        new Thread(() -> {
            try {
                ServerConnection conn = ServerConnection.getInstance();
                conn.send(new GetSellerItemsRequest(currentUser.getId()));

                ServerMessageListener listener = ClientApp.getListener();
                Object response = listener.waitForResponse();

                Platform.runLater(() -> handleGetItemsResponse(response));
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() ->
                        AlertUtils.showError("Lỗi mạng", "Không tải được danh sách: " + e.getMessage()));
            }
        }).start();
    }

    private void handleGetItemsResponse(Object response) {
        if (response instanceof GetSellerItemsResponse resp) {
            if (resp.success()) {
                List<Item> items = resp.items();
                tblItems.setItems(FXCollections.observableArrayList(items));
            } else {
                AlertUtils.showError("Lỗi", resp.message());
            }
        }
    }

    @FXML
    public void onAddClicked() {
        User currentUser = ClientSession.getCurrentUser();
        if (currentUser == null) {
            AlertUtils.showWarning("Chưa đăng nhập", "Cần đăng nhập");
            return;
        }

        String name = txtName.getText().trim();
        String description = txtDescription.getText().trim();
        String priceStr = txtStartingPrice.getText().trim();
        ItemType type = cbItemType.getValue();

        // Validate
        if (name.isEmpty()) {
            AlertUtils.showWarning("Lỗi", "Nhập tên sản phẩm");
            return;
        }
        if (type == null) {
            AlertUtils.showWarning("Lỗi", "Chọn loại sản phẩm");
            return;
        }
        long startPrice;
        try {
            startPrice = Long.parseLong(priceStr);
        } catch (NumberFormatException ex) {
            AlertUtils.showError("Lỗi nhập liệu", "Giá khởi điểm phải là số");
            return;
        }
        if (startPrice <= 0) {
            AlertUtils.showWarning("Lỗi", "Giá phải > 0");
            return;
        }

        // Gửi request qua server
        new Thread(() -> {
            try {
                ServerConnection conn = ServerConnection.getInstance();
                conn.send(new AddItemRequest(name, description, startPrice, type, currentUser.getId()));

                ServerMessageListener listener = ClientApp.getListener();
                Object response = listener.waitForResponse();

                Platform.runLater(() -> handleAddResponse(response));
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() ->
                        AlertUtils.showError("Lỗi mạng", "Không gửi được: " + e.getMessage()));
            }
        }).start();
    }

    private void handleAddResponse(Object response) {
        if (response instanceof AddItemResponse resp) {
            if (resp.success()) {
                AlertUtils.showInfo("Thành công", resp.message());
                clearForm();
                loadSellerProducts();
            } else {
                AlertUtils.showError("Lỗi", resp.message());
            }
        }
    }

    @FXML
    public void onUpdateClicked() {
        if (selectedItem == null) {
            AlertUtils.showWarning("Chưa chọn sản phẩm", "Chọn sản phẩm để sửa");
            return;
        }

        User currentUser = ClientSession.getCurrentUser();
        String name = txtName.getText().trim();
        String description = txtDescription.getText().trim();
        String priceStr = txtStartingPrice.getText().trim();
        ItemType type = cbItemType.getValue();

        long startPrice;
        try {
            startPrice = Long.parseLong(priceStr);
        } catch (NumberFormatException ex) {
            AlertUtils.showError("Lỗi", "Giá phải là số");
            return;
        }

        new Thread(() -> {
            try {
                ServerConnection conn = ServerConnection.getInstance();
                conn.send(new UpdateItemRequest(
                        selectedItem.getId(), name, description, startPrice, type, currentUser.getId()));

                ServerMessageListener listener = ClientApp.getListener();
                Object response = listener.waitForResponse();

                Platform.runLater(() -> handleUpdateResponse(response));
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() ->
                        AlertUtils.showError("Lỗi mạng", e.getMessage()));
            }
        }).start();
    }

    private void handleUpdateResponse(Object response) {
        if (response instanceof UpdateItemResponse resp) {
            if (resp.success()) {
                AlertUtils.showInfo("Thành công", resp.message());
                clearForm();
                loadSellerProducts();
            } else {
                AlertUtils.showError("Lỗi", resp.message());
            }
        }
    }

    @FXML
    public void onDeleteClicked() {
        if (selectedItem == null) {
            AlertUtils.showWarning("Chưa chọn sản phẩm", "Chọn sản phẩm để xoá");
            return;
        }

        String itemId = selectedItem.getId();

        new Thread(() -> {
            try {
                ServerConnection conn = ServerConnection.getInstance();
                conn.send(new DeleteItemRequest(itemId));

                ServerMessageListener listener = ClientApp.getListener();
                Object response = listener.waitForResponse();

                Platform.runLater(() -> handleDeleteResponse(response));
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() ->
                        AlertUtils.showError("Lỗi mạng", e.getMessage()));
            }
        }).start();
    }

    private void handleDeleteResponse(Object response) {
        if (response instanceof DeleteItemResponse resp) {
            if (resp.success()) {
                AlertUtils.showInfo("Thành công", resp.message());
                clearForm();
                loadSellerProducts();
            } else {
                AlertUtils.showError("Lỗi", resp.message());
            }
        }
    }

    @FXML
    public void onClearClicked() {
        clearForm();
    }

    @FXML
    public void onBackHome() {
        SceneNavigator.switchScene("/fxml/MainLayout.fxml");
    }

    private void fillForm(Item item) {
        txtName.setText(item.getName());
        txtDescription.setText(item.getDescription());
        txtStartingPrice.setText(String.valueOf(item.getStartPrice()));
        cbItemType.setValue(ItemFactory.toItemType(item));
    }

    private void clearForm() {
        selectedItem = null;
        tblItems.getSelectionModel().clearSelection();
        txtName.clear();
        txtDescription.clear();
        txtStartingPrice.clear();
        cbItemType.setValue(null);
    }
}