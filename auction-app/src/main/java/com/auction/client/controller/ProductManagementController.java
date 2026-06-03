package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.EnumFormatter;
import com.auction.client.util.MoneyFormatter;
import com.auction.client.util.NavRouter;
import com.auction.client.util.RequestExecutor;
import com.auction.client.util.SceneNavigator;
import com.auction.client.util.SidebarBuilder.NavKey;
import com.auction.client.util.TopbarBuilder;
import com.auction.shared.factory.ItemFactory;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.item.ItemType;
import com.auction.shared.model.user.User;
import com.auction.shared.networkMessage.Requests.*;
import com.auction.shared.networkMessage.Requests.AddItemRequest;
import com.auction.shared.networkMessage.Requests.GetSellerItemsRequest;
import com.auction.shared.networkMessage.Requests.UpdateItemRequest;
import com.auction.shared.networkMessage.Results.*;
import com.auction.shared.networkMessage.Results.AddItemResult;
import com.auction.shared.networkMessage.Results.GetSellerItemsResult;
import com.auction.shared.networkMessage.Results.UpdateItemResult;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
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
    @FXML private TextField txtMinIncrement;     // tùy chọn — null = polymorphism default
    @FXML private ComboBox<ItemType> cbItemType;
    @FXML private DatePicker dpStartDate;
    @FXML private TextField txtStartTime;
    @FXML private DatePicker dpEndDate;
    @FXML private TextField txtEndTime;
    @FXML private StackPane topbarContainer;

    private Item selectedItem;
    private java.time.LocalDateTime lastSentStart;
    private java.time.LocalDateTime lastSentEnd;

    @FXML
    public void initialize() {
        // Build sidebar Seller
        if (topbarContainer != null && ClientSession.getCurrentUser() != null) {
            var user = ClientSession.getCurrentUser();
            var topbar = TopbarBuilder.build(
                    user,
                    NavKey.SELLER_PRODUCTS,
                    this::handleNavClick,
                    this::handleLogout
            );
            topbarContainer.getChildren().add(topbar);
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

        applyDefaultSchedule();

        loadSellerProducts();
    }

    /** Gợi ý giá trị mặc định cho 4 control (user có thể sửa tùy ý trước khi bấm Thêm). */
    private void applyDefaultSchedule() {
        java.time.LocalDateTime start = java.time.LocalDateTime.now().plusMinutes(5)
                .withSecond(0).withNano(0);
        java.time.LocalDateTime end = start.plusHours(24);

        var hhmm = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        if (dpStartDate != null) dpStartDate.setValue(start.toLocalDate());
        if (txtStartTime != null) txtStartTime.setText(start.toLocalTime().format(hhmm));
        if (dpEndDate != null) dpEndDate.setValue(end.toLocalDate());
        if (txtEndTime != null) txtEndTime.setText(end.toLocalTime().format(hhmm));
    }

    private void handleNavClick(NavKey key) {
        if (key == NavKey.SELLER_PRODUCTS) return; // đang ở đây
        NavRouter.route(key);
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

        RequestExecutor.send(
                new GetSellerItemsRequest(currentUser.getId()),
                this::handleGetSellerItemsResult,
                error -> AlertUtils.showError("Lỗi mạng", "Không tải được danh sách: " + error)
        );
    }

    private void handleGetSellerItemsResult(Object response) {
        if (response instanceof GetSellerItemsResult result) {
            switch (result) {
                case GetSellerItemsResult.Success s -> {
                    tblItems.setItems(FXCollections.observableArrayList(s.items()));
                }
                case GetSellerItemsResult.Failure f -> {
                    AlertUtils.showError("Lỗi", f.reason());
                }
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

        // Bước giá tùy chọn: để trống = polymorphism, có giá trị = dùng giá trị seller chọn
        Long customMinIncrement;
        try {
            customMinIncrement = parseCustomMinIncrement();
        } catch (IllegalArgumentException ex) {
            AlertUtils.showError("Lỗi nhập liệu", ex.getMessage());
            return;
        }
        if (customMinIncrement != null) {
            if (customMinIncrement <= 0) {
                AlertUtils.showWarning("Lỗi", "Bước giá phải lớn hơn 0");
                return;
            }
            long minAllowed = Math.max(1L, Math.round(startPrice * 0.05));
            if (customMinIncrement < minAllowed) {
                AlertUtils.showWarning("Lỗi",
                        "Bước giá tối thiểu phải bằng 5% giá khởi điểm ("
                                + MoneyFormatter.formatVnd(minAllowed) + ")");
                return;
            }
            if (customMinIncrement >= startPrice) {
                AlertUtils.showWarning("Lỗi", "Bước giá phải nhỏ hơn giá khởi điểm");
                return;
            }
        }

        java.time.LocalDateTime startTime = combineDateTime(dpStartDate, txtStartTime);
        if (startTime == null) {
            AlertUtils.showWarning("Lỗi", "Thời gian bắt đầu không hợp lệ (định dạng HH:mm)");
            return;
        }
        java.time.LocalDateTime endTime = combineDateTime(dpEndDate, txtEndTime);
        if (endTime == null) {
            AlertUtils.showWarning("Lỗi", "Thời gian kết thúc không hợp lệ (định dạng HH:mm)");
            return;
        }
        if (!endTime.isAfter(startTime)) {
            AlertUtils.showWarning("Lỗi", "Thời gian kết thúc phải sau thời gian bắt đầu");
            return;
        }
        if (startTime.isBefore(java.time.LocalDateTime.now().minusMinutes(1))) {
            AlertUtils.showWarning("Lỗi", "Thời gian bắt đầu không được trong quá khứ");
            return;
        }

        lastSentStart = startTime;
        lastSentEnd = endTime;

        RequestExecutor.send(
                new AddItemRequest(name, description, startPrice, type,
                        currentUser.getId(), startTime, endTime,
                        customMinIncrement),
                this::handleAddItemResult,
                error -> AlertUtils.showError("Lỗi mạng", "Không gửi được: " + error)
        );
    }

    private void handleAddItemResult(Object response) {
        if (response instanceof AddItemResult result) {
            switch (result) {
                case AddItemResult.Success s -> {
                    AlertUtils.showInfo("Thành công",
                            "Đã thêm sản phẩm. Phiên đấu giá: "
                                    + formatRange(lastSentStart, lastSentEnd));
                    clearForm();
                    loadSellerProducts();
                }
                case AddItemResult.Failure f -> {
                    AlertUtils.showError("Lỗi", f.reason());
                }
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

        RequestExecutor.send(
                new UpdateItemRequest(selectedItem.getId(), name, description,
                        startPrice, type, currentUser.getId()),
                this::handleUpdateItemResult,
                error -> AlertUtils.showError("Lỗi mạng", error)
        );
    }

    private void handleUpdateItemResult(Object response) {
        if (response instanceof UpdateItemResult result) {
            switch (result) {
                case UpdateItemResult.Success s -> {
                    AlertUtils.showInfo("Thành công", "Đã cập nhật sản phẩm");
                    clearForm();
                    loadSellerProducts();
                }
                case UpdateItemResult.Failure f -> {
                    AlertUtils.showError("Lỗi", f.reason());
                }
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
        if (txtMinIncrement != null) txtMinIncrement.clear();
        cbItemType.setValue(null);
        applyDefaultSchedule();
    }

    /**
     * Parse ô "Bước giá":
     *   - Trống → null (server sẽ dùng polymorphism Item.suggestedMinIncrement)
     *   - Có số → Long value (server dùng giá trị seller chọn)
     *   - Không phải số → throw IllegalArgumentException để caller hiển thị lỗi
     */
    private Long parseCustomMinIncrement() {
        if (txtMinIncrement == null) return null;
        String raw = txtMinIncrement.getText();
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            // Chấp nhận user nhập "100.000" hoặc "100,000" — xóa dấu chấm/phẩy/khoảng trắng
            String cleaned = raw.trim().replaceAll("[.,\\s]", "");
            return Long.parseLong(cleaned);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Bước giá phải là số");
        }
    }

    /** Format khoảng thời gian "dd/MM HH:mm → dd/MM HH:mm". */
    private static String formatRange(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        if (start == null || end == null) return "—";
        var fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm");
        return start.format(fmt) + " → " + end.format(fmt);
    }

    /** Gộp DatePicker + TextField "HH:mm" thành LocalDateTime. null nếu invalid. */
    private static java.time.LocalDateTime combineDateTime(DatePicker dp, TextField tf) {
        if (dp == null || dp.getValue() == null) return null;
        String hhmm = tf == null ? null : tf.getText();
        if (hhmm == null || hhmm.isBlank()) return null;
        try {
            java.time.LocalTime t = java.time.LocalTime.parse(
                    hhmm.trim(), java.time.format.DateTimeFormatter.ofPattern("H:mm"));
            return java.time.LocalDateTime.of(dp.getValue(), t);
        } catch (java.time.format.DateTimeParseException ex) {
            return null;
        }
    }
}