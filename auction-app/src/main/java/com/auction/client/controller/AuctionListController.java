package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.network.ServerConnection;
import com.auction.client.realtime.AuctionEventBus;
import com.auction.client.realtime.AuctionEventObserver;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.SceneNavigator;
import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.FileAuctionDao;
import com.auction.server.service.AuctionLifecycleService;
import com.auction.server.service.DefaultAuctionLifecycleService;
import com.auction.shared.model.Auction;
import com.auction.shared.network.AuctionUpdateEvent;
import com.auction.shared.network.SubscribeAuctionListRequest;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AuctionListController implements AuctionEventObserver {

    @FXML
    private ListView<String> auctionListView;

    private final AuctionDao auctionDao = new FileAuctionDao();
    private final AuctionLifecycleService lifecycleService =
            new DefaultAuctionLifecycleService(auctionDao);

    private final List<Auction> currentAuctions = new ArrayList<>();

    public void initialize() {
        lifecycleService.updateAllAuctionStatuses();

        AuctionEventBus.getInstance().addObserver(this);
        try {
            ServerConnection.getInstance()
                    .send(new SubscribeAuctionListRequest());
        } catch (IOException e) {
            AlertUtils.showError("Lỗi kết nối", "Không thể subscribe danh sách auction: " + e.getMessage());
        }

        loadActiveAuctions();
    }

    public void loadActiveAuctions() {
        currentAuctions.clear();
        currentAuctions.addAll(auctionDao.findActiveAuctions());

        auctionListView.getItems().clear();

        if (currentAuctions.isEmpty()) {
            auctionListView.getItems().add("Không có auction đang mở");
            return;
        }

        for (Auction auction : currentAuctions) {
            auctionListView.getItems().add(formatAuctionLine(auction));
        }
    }

    public void onRefreshClicked() {
        lifecycleService.updateAllAuctionStatuses();
        loadActiveAuctions();
    }

    public void onViewDetail() {
        int selectedIndex = auctionListView.getSelectionModel().getSelectedIndex();

        if (selectedIndex < 0 || selectedIndex >= currentAuctions.size()) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Hãy chọn một auction trước.");
            return;
        }

        Auction selectedAuction = currentAuctions.get(selectedIndex);
        ClientSession.setSelectedAuctionId(selectedAuction.getId());
        SceneNavigator.switchScene("/fxml/AuctionDetail.fxml");
    }

    public void onOpenDetailClicked() {
        onViewDetail();
    }

    public void onBackHome() {
        SceneNavigator.switchScene("/fxml/MainLayout.fxml");
    }

    private String formatAuctionLine(Auction auction) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

        return "Auction ID: " + auction.getId()
                + " | Item: " + auction.getItemId()
                + " | Giá: " + formatMoney(auction.getCurrentPrice())
                + " | Trạng thái: " + auction.getStatus()
                + " | Kết thúc: " + auction.getEndTime().format(formatter);
    }

    private String formatMoney(long amount) {
        return String.format("%,d VNĐ", amount);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @Override
    public void onAuctionUpdated(AuctionUpdateEvent event) {
        Auction updated = event.getAuction();
        updateAuctionRow(updated);
    }

    private void updateAuctionRow(Auction updated) {
        for (int i = 0; i < currentAuctions.size(); i++) {
            Auction oldAuction = currentAuctions.get(i);

            if (oldAuction.getId().equals(updated.getId())) {
                currentAuctions.set(i, updated);
                auctionListView.getItems().set(i, formatAuctionLine(updated));
                return;
            }
        }

        // Nếu auction mới chưa có trong danh sách nhưng đang active thì thêm vào
        if (updated.isRunning()) {
            currentAuctions.add(updated);
            auctionListView.getItems().add(formatAuctionLine(updated));
        }
    }

    public void dispose() {
        AuctionEventBus.getInstance().removeObserver(this);
    }
}