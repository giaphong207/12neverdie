package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.util.SceneNavigator;
import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.FileAuctionDao;
import com.auction.server.service.AuctionLifecycleService;
import com.auction.server.service.DefaultAuctionLifecycleService;
import com.auction.shared.model.Auction;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AuctionListController {

    @FXML
    private ListView<String> auctionListView;

    private final AuctionDao auctionDao = new FileAuctionDao();
    private final AuctionLifecycleService lifecycleService =
            new DefaultAuctionLifecycleService(auctionDao);

    private final List<Auction> currentAuctions = new ArrayList<>();

    public void initialize() {
        lifecycleService.updateAllAuctionStatuses();
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
}