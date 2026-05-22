package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.network.ServerConnection;
import com.auction.client.realtime.AuctionEventBus;
import com.auction.client.realtime.AuctionEventObserver;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.AuctionCardBuilder;
import com.auction.client.util.EnumFormatter;
import com.auction.client.util.SceneNavigator;
import com.auction.client.util.SidebarBuilder;
import com.auction.client.util.SidebarBuilder.NavKey;
import com.auction.shared.model.Auction;
import com.auction.shared.network.AuctionEvent;
import com.auction.shared.model.AuctionStatus;
import com.auction.shared.model.Role;
import com.auction.shared.network.SubscribeAuctionListRequest;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AuctionListController implements AuctionEventObserver {

    @FXML private StackPane sidebarContainer;
    @FXML private Label resultCountLabel;
    @FXML private TextField searchField;
    @FXML private ChoiceBox<AuctionStatus> statusFilter;
    @FXML private ChoiceBox<String> typeFilter;
    @FXML private FlowPane auctionGrid;
    @FXML private VBox emptyState;

    private final List<Auction> allAuctions = new ArrayList<>();

    // Filter "Tất cả" cho status & type
    private static final String TYPE_ALL = "Tất cả loại";

    @FXML
    public void initialize() {
        // Sidebar tuỳ theo role
        if (sidebarContainer != null && ClientSession.getCurrentUser() != null) {
            var user = ClientSession.getCurrentUser();
            NavKey activeKey = switch (user.getRole()) {
                case BIDDER -> NavKey.BIDDER_LIVE;
                case ADMIN -> NavKey.ADMIN_AUCTIONS;
                default -> NavKey.BIDDER_LIVE;
            };
            var sidebar = SidebarBuilder.build(user, activeKey, this::handleNavClick, this::handleLogout);
            sidebarContainer.getChildren().add(sidebar);
        }

        // Setup filter status
        statusFilter.setItems(FXCollections.observableArrayList(
                null, // null = "Tất cả"
                AuctionStatus.OPEN,
                AuctionStatus.RUNNING,
                AuctionStatus.FINISHED,
                AuctionStatus.PAID,
                AuctionStatus.CANCELED
        ));
        statusFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(AuctionStatus status) {
                return status == null ? "Tất cả trạng thái" : EnumFormatter.auctionStatusVi(status);
            }
            @Override
            public AuctionStatus fromString(String s) { return null; }
        });
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.setOnAction(e -> applyFilters());

        // Setup filter type
        typeFilter.setItems(FXCollections.observableArrayList(
                TYPE_ALL,
                "Đồ điện tử",
                "Tác phẩm nghệ thuật",
                "Phương tiện"
        ));
        typeFilter.getSelectionModel().selectFirst();
        typeFilter.setOnAction(e -> applyFilters());

        // Setup search
        searchField.textProperty().addListener((obs, oldV, newV) -> applyFilters());

        // Subscribe data
        AuctionEventBus.getInstance().addObserver(this);
        try {
            ServerConnection.getInstance().send(new SubscribeAuctionListRequest());
        } catch (IOException e) {
            AlertUtils.showError("Lỗi kết nối", "Không tải được danh sách: " + e.getMessage());
        }
    }


    private int indexOfAuction(String id) {
        for (int i = 0; i < allAuctions.size(); i++) {
            if (allAuctions.get(i).getId().equals(id)) return i;
        }
        return -1;
    }

    private void applyFilters() {
        AuctionStatus selectedStatus = statusFilter.getSelectionModel().getSelectedItem();
        String selectedType = typeFilter.getSelectionModel().getSelectedItem();
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();

        List<Auction> filtered = new ArrayList<>();
        for (Auction a : allAuctions) {
            // Status filter
            if (selectedStatus != null && a.getStatus() != selectedStatus) continue;

            // Search filter (theo auction ID)
            if (!search.isEmpty()) {
                boolean matchId = a.getId() != null && a.getId().toLowerCase().contains(search);
                if (!matchId) continue;
            }

            // Type filter — vì code không có field type trên Auction, ta skip filter này khi user chọn "Tất cả"
            // Phase 5 có thể nâng cấp thêm: lookup Item.type qua itemId
            if (selectedType != null && !TYPE_ALL.equals(selectedType)) {
                // Bỏ qua phần filter type tạm thời — sẽ làm sau khi có ItemDao client-side
                // Hiện tại tất cả auctions sẽ qua filter này
            }

            filtered.add(a);
        }

        // Sort: phiên RUNNING lên đầu, sau đó theo endTime gần nhất
        filtered.sort(Comparator
                .comparing((Auction a) -> a.getStatus() != AuctionStatus.RUNNING)
                .thenComparing(a -> a.getEndTime() == null
                        ? java.time.LocalDateTime.MAX
                        : a.getEndTime()));

        renderGrid(filtered);
    }

    private void renderGrid(List<Auction> auctions) {
        auctionGrid.getChildren().clear();

        if (auctions.isEmpty()) {
            auctionGrid.setVisible(false);
            auctionGrid.setManaged(false);
            emptyState.setVisible(true);
            emptyState.setManaged(true);
            resultCountLabel.setText("Không có kết quả");
            return;
        }

        auctionGrid.setVisible(true);
        auctionGrid.setManaged(true);
        emptyState.setVisible(false);
        emptyState.setManaged(false);

        for (Auction a : auctions) {
            var card = AuctionCardBuilder.build(a, this::openAuctionDetail);
            auctionGrid.getChildren().add(card);
        }

        resultCountLabel.setText("Hiển thị " + auctions.size() + " phiên đấu giá");
    }

    private void openAuctionDetail(Auction auction) {
        ClientSession.setSelectedAuctionId(auction.getId());
        SceneNavigator.switchScene("/fxml/AuctionDetail.fxml");
    }

    @FXML
    private void onRefreshClicked() {
        try {
            allAuctions.clear();
            applyFilters();
            ServerConnection.getInstance().send(new SubscribeAuctionListRequest());
        } catch (IOException e) {
            AlertUtils.showError("Lỗi", "Không làm mới được: " + e.getMessage());
        }
    }

    private void handleNavClick(NavKey key) {
        switch (key) {
            // Bidder routes
            case BIDDER_HOME -> SceneNavigator.switchScene("/fxml/BidderDashboard.fxml");
            case BIDDER_LIVE -> { /* đang ở đây */ }
            case BIDDER_MINE -> SceneNavigator.switchScene("/fxml/MyAuctions.fxml");
            case BIDDER_WON -> SceneNavigator.switchScene("/fxml/WonAuctions.fxml");

            // Seller routes
            case SELLER_OVERVIEW -> SceneNavigator.switchScene("/fxml/SellerDashboard.fxml");
            case SELLER_PRODUCTS -> SceneNavigator.switchScene("/fxml/ProductManagement.fxml");
            case SELLER_AUCTIONS -> SceneNavigator.switchScene("/fxml/SellerAuctions.fxml");

            // Admin routes
            case ADMIN_OVERVIEW -> SceneNavigator.switchScene("/fxml/AdminDashboard.fxml");
            case ADMIN_AUCTIONS -> { /* đang ở đây */ }

            default -> AlertUtils.showInfo("Sắp ra mắt", "Tính năng này đang được phát triển.");
        }
    }

    @Override
    public void onAuctionUpdated(AuctionEvent event) {
        Auction updated = event.getAuction();
        Platform.runLater(() -> updateAuctionRow(updated));   // ← Wrap!
    }

    private void updateAuctionRow(Auction updated) {
        int idx = indexOfAuction(updated.getId());
        if (idx >= 0) allAuctions.set(idx, updated);
        else allAuctions.add(updated);
        applyFilters();
    }

    private void handleLogout() {
        AuctionEventBus.getInstance().removeObserver(this);
        ClientSession.clear();
        SceneNavigator.switchScene("/fxml/Login.fxml");
    }
}