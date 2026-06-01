package com.auction.client.controller;

import java.util.Locale;

import com.auction.client.context.ClientSession;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.Disposable;
import com.auction.client.util.NavRouter;
import com.auction.client.util.RequestExecutor;
import com.auction.client.util.SceneNavigator;
import com.auction.client.util.SidebarBuilder.NavKey;
import com.auction.client.util.TopbarBuilder;
import com.auction.shared.factory.ItemFactory;
import com.auction.shared.model.item.Item;
import com.auction.shared.networkMessage.Requests.AdminDeleteItemRequest;
import com.auction.shared.networkMessage.Requests.GetAllItemsRequest;
import com.auction.shared.networkMessage.Results.AdminDeleteItemResult;
import com.auction.shared.networkMessage.Results.GetAllItemsResult;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/**
 * Trang "Sản phẩm" cho quản trị viên: xem toàn bộ sản phẩm và gỡ sản phẩm vi phạm.
 */
public class AdminProductsController implements Disposable {

    @FXML private StackPane topbarContainer;
    @FXML private Label summaryLabel;
    @FXML private TableView<Item> productsTable;
    @FXML private TableColumn<Item, String> colName;
    @FXML private TableColumn<Item, String> colSeller;
    @FXML private TableColumn<Item, String> colType;
    @FXML private TableColumn<Item, String> colAction;
    @FXML private TextField searchField;

    private final ObservableList<Item> allItems = FXCollections.observableArrayList();
    private FilteredList<Item> filtered;

    @FXML
    public void initialize() {
        if (topbarContainer != null && ClientSession.getCurrentUser() != null) {
            var topbar = TopbarBuilder.build(
                    ClientSession.getCurrentUser(),
                    NavKey.ADMIN_PRODUCTS,
                    this::handleNavClick,
                    this::handleLogout
            );
            topbarContainer.getChildren().add(topbar);
        }

        setupColumns();

        filtered = new FilteredList<>(allItems, i -> true);
        if (productsTable != null) productsTable.setItems(filtered);

        if (searchField != null) {
            searchField.textProperty().addListener((obs, old, text) -> applyFilter(text));
        }

        loadItems();
    }

    private void setupColumns() {
        if (colName != null) {
            colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        }
        if (colSeller != null) {
            colSeller.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSellerId()));
        }
        if (colType != null) {
            colType.setCellValueFactory(c ->
                    new SimpleStringProperty(ItemFactory.toItemType(c.getValue()).name()));
        }
        if (colAction != null) {
            // Cột này không gắn dữ liệu cụ thể, chỉ chứa nút Gỡ
            colAction.setCellValueFactory(c -> new SimpleStringProperty(""));
            colAction.setCellFactory(col -> new TableCell<>() {
                private final Button removeBtn = new Button("Gỡ");
                {
                    removeBtn.getStyleClass().add("btn-danger");
                    removeBtn.setOnAction(e -> {
                        Item item = getTableView().getItems().get(getIndex());
                        confirmAndRemove(item);
                    });
                }
                @Override protected void updateItem(String ignored, boolean empty) {
                    super.updateItem(ignored, empty);
                    if (empty || getIndex() >= getTableView().getItems().size()) {
                        setGraphic(null);
                        return;
                    }
                    HBox box = new HBox(removeBtn);
                    box.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(box);
                }
            });
        }
    }

    private void confirmAndRemove(Item item) {
        if (item == null) return;
        boolean ok = AlertUtils.showConfirm("Xác nhận gỡ sản phẩm",
                "Gỡ sản phẩm \"" + item.getName() + "\"? Phiên đấu giá của nó (nếu có) "
                        + "cũng sẽ bị xóa. Hành động không thể hoàn tác.");
        if (!ok) return;

        RequestExecutor.send(
                new AdminDeleteItemRequest(item.getId()),
                response -> {
                    if (response instanceof AdminDeleteItemResult result) {
                        switch (result) {
                            case AdminDeleteItemResult.Success s -> {
                                AlertUtils.showInfo("Thành công", "Đã gỡ sản phẩm.");
                                loadItems(); // load lại để dòng đó biến mất
                            }
                            case AdminDeleteItemResult.Failure f ->
                                    AlertUtils.showError("Gỡ thất bại", f.reason());
                        }
                    }
                },
                error -> AlertUtils.showError("Lỗi mạng", "Không gỡ được sản phẩm: " + error)
        );
    }

    private void applyFilter(String text) {
        if (filtered == null) return;
        String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        filtered.setPredicate(i -> q.isEmpty()
                || (i.getName() != null && i.getName().toLowerCase(Locale.ROOT).contains(q)));
        if (summaryLabel != null) {
            summaryLabel.setText(q.isEmpty()
                    ? "Tổng " + allItems.size() + " sản phẩm"
                    : "Tìm thấy " + filtered.size() + " / " + allItems.size() + " sản phẩm");
        }
    }

    private void loadItems() {
        RequestExecutor.send(
                new GetAllItemsRequest(),
                response -> {
                    if (response instanceof GetAllItemsResult result) {
                        switch (result) {
                            case GetAllItemsResult.Success s -> {
                                allItems.setAll(s.items());
                                applyFilter(searchField == null ? "" : searchField.getText());
                            }
                            case GetAllItemsResult.Failure f ->
                                    AlertUtils.showError("Lỗi", "Không tải được sản phẩm: " + f.reason());
                        }
                    }
                },
                error -> AlertUtils.showError("Lỗi mạng", "Không tải được sản phẩm: " + error)
        );
    }

    private void handleNavClick(NavKey key) {
        if (key == NavKey.ADMIN_PRODUCTS) return; // đang ở đây
        NavRouter.route(key);
    }

    private void handleLogout() {
        ClientSession.clear();
        SceneNavigator.switchScene("/fxml/Login.fxml");
    }

    @Override
    public void dispose() {
        //ko subscribe event nào nên ko cần dọn dẹp.
    }
}