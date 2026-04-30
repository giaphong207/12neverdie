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

import com.auction.client.util.AlertUtils;
import com.auction.shared.exception.AppException;

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
        try {
            User currentUser = ClientSession.getCurrentUser();

            if (currentUser == null) {
                AlertUtils.showWarning("Chưa đăng nhập", "Bạn cần đăng nhập để xem sản phẩm");
                return;
            }

            String sellerId = currentUser.getId();
            List<Item> items = itemService.getItemsBySeller(sellerId);

            tblItems.setItems(FXCollections.observableArrayList(items));
        } catch (AppException ex) {
            AlertUtils.showError("Lỗi tải sản phẩm", ex.getMessage());
        }
    }

    @FXML
    public void onAddClicked() {
        try {
            Item item = buildItemFromForm();

            itemService.addItem(item);

            loadSellerProducts();
            clearForm();

            AlertUtils.showInfo("Thành công", "Đã thêm sản phẩm");
        } catch (NumberFormatException ex) {
            AlertUtils.showError("Lỗi nhập liệu", "Giá khởi điểm phải là số");
        } catch (AppException ex) {
            AlertUtils.showError("Lỗi sản phẩm", ex.getMessage());
        }
    }

    @FXML
    public void onUpdateClicked() {
        try {
            if (selectedItem == null) {
                AlertUtils.showWarning("Chưa chọn sản phẩm", "Bạn chưa chọn sản phẩm để sửa");
                return;
            }

            Item updatedItem = buildItemFromFormWithId(selectedItem.getId());

            itemService.updateItem(updatedItem);

            loadSellerProducts();
            clearForm();

            AlertUtils.showInfo("Thành công", "Đã cập nhật sản phẩm");
        } catch (NumberFormatException ex) {
            AlertUtils.showError("Lỗi nhập liệu", "Giá khởi điểm phải là số");
        } catch (AppException ex) {
            AlertUtils.showError("Lỗi sản phẩm", ex.getMessage());
        }
    }

    @FXML
    public void onDeleteClicked() {
        try {
            if (selectedItem == null) {
                AlertUtils.showWarning("Chưa chọn sản phẩm", "Bạn chưa chọn sản phẩm để xoá");
                return;
            }

            itemService.deleteItem(selectedItem.getId());

            loadSellerProducts();
            clearForm();

            AlertUtils.showInfo("Thành công", "Đã xoá sản phẩm");
        } catch (AppException ex) {
            AlertUtils.showError("Lỗi sản phẩm", ex.getMessage());
        }
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
}


