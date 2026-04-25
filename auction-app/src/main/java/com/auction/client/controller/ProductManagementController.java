package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.server.dao.FileItemDao;
import com.auction.server.service.DefaultItemService;
import com.auction.server.service.ItemService;
import com.auction.shared.model.Item;
import com.auction.shared.model.ItemType;
import com.auction.shared.model.User;
import com.auction.shared.pattern.ItemFactory;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.List;
import java.util.UUID;


import com.auction.client.util.SceneNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class ProductManagementController {

    @FXML
    private TextField productNameField;

    @FXML
    private TextField priceField;

    @FXML
    private Label messageLabel;

    @FXML
    private TableView<Item> tblItems;

    @FXML
    private TableColumn<Item, String> colName;

    @FXML
    private TableColumn<Item, String> colDescription;

    @FXML
    private TableColumn<Item, String> colStartPrice;

    @FXML
    private TextField txtName;

    @FXML
    private TextArea txtDescription;

    @FXML
    private TextField txtStartPrice;

    @FXML
    private ComboBox<ItemType> cbItemType;

    private final ItemService itemService =
            new DefaultItemService(new FileItemDao());

    private Item selectedItem;

    public void onAddProduct() {
        String name = productNameField.getText();
        String price = priceField.getText();
        messageLabel.setText("Mock add product: " + name + " - " + price);
    }

    public void onBackHome() {
        SceneNavigator.switchScene("/fxml/MainLayout.fxml");
    }

    @FXML
    public void initialize() {
        cbItemType.setItems(FXCollections.observableArrayList(ItemType.values()));

        tblItems.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldItem, newItem) -> {
                    selectedItem = newItem;
                    if (newItem != null) {
                        fillForm(newItem);
                    }
                });

        loadSellerProducts();
    }

    public void loadSellerProducts() {
        User currentUser = ClientSession.getCurrentUser();

        if (currentUser == null) {
            showAlert("Chưa đăng nhập");
            return;
        }

        String sellerId = currentUser.getId();
        List<Item> items = itemService.getItemsBySeller(sellerId);

        tblItems.setItems(FXCollections.observableArrayList(items));
    }

    @FXML
    public void onAddClicked() {
        if (!validateForm()) {
            return;
        }

        Item item = buildItemFromForm();
        itemService.addItem(item);

        loadSellerProducts();
        clearForm();
    }

    @FXML
    public void onUpdateClicked() {
        if (selectedItem == null) {
            showAlert("Bạn chưa chọn sản phẩm để sửa");
            return;
        }

        if (!validateForm()) {
            return;
        }

        Item updatedItem = buildItemFromFormWithId(selectedItem.getId());
        itemService.updateItem(updatedItem);

        loadSellerProducts();
        clearForm();
    }

    @FXML
    public void onDeleteClicked() {
        if (selectedItem == null) {
            showAlert("Bạn chưa chọn sản phẩm để xoá");
            return;
        }

        itemService.deleteItem(selectedItem.getId());

        loadSellerProducts();
        clearForm();
    }

    @FXML
    public void onClearClicked() {
        clearForm();
    }

    private Item buildItemFromForm() {
        String id = UUID.randomUUID().toString();
        return buildItemFromFormWithId(id);
    }

    private Item buildItemFromFormWithId(String id) {
        User currentUser = ClientSession.getCurrentUser();
        String sellerId = currentUser.getId();

        String name = txtName.getText().trim();
        String description = txtDescription.getText().trim();
        long startPrice = Long.parseLong(txtStartPrice.getText().trim());
        ItemType type = cbItemType.getValue();

        return ItemFactory.createItem(
                type,
                id,
                sellerId,
                name,
                description,
                startPrice
        );
    }

    private boolean validateForm() {
        if (txtName.getText() == null || txtName.getText().isBlank()) {
            showAlert("Tên sản phẩm không được rỗng");
            return false;
        }

        if (txtDescription.getText() == null || txtDescription.getText().isBlank()) {
            showAlert("Mô tả không được rỗng");
            return false;
        }

        if (txtStartPrice.getText() == null || txtStartPrice.getText().isBlank()) {
            showAlert("Giá khởi điểm không được rỗng");
            return false;
        }

        try {
            double price = Double.parseDouble(txtStartPrice.getText().trim());
            if (price <= 0) {
                showAlert("Giá khởi điểm phải lớn hơn 0");
                return false;
            }
        } catch (NumberFormatException e) {
            showAlert("Giá khởi điểm phải là số");
            return false;
        }

        if (cbItemType.getValue() == null) {
            showAlert("Bạn chưa chọn loại sản phẩm");
            return false;
        }

        return true;
    }

    private void fillForm(Item item) {
        txtName.setText(item.getName());
        txtDescription.setText(item.getDescription());
        txtStartPrice.setText(String.valueOf(item.getStartPrice()));
        cbItemType.setValue(item.getType());
    }

    private void clearForm() {
        selectedItem = null;
        tblItems.getSelectionModel().clearSelection();

        txtName.clear();
        txtDescription.clear();
        txtStartPrice.clear();
        cbItemType.setValue(null);
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}


