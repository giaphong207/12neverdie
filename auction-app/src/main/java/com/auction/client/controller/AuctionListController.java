package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.util.SceneNavigator;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;

public class AuctionListController {

    @FXML
    private ListView<String> auctionListView;

    public void initialize() {
        auctionListView.setItems(FXCollections.observableArrayList(
                "Auction 1 - iPhone 15",
                "Auction 2 - MacBook Air",
                "Auction 3 - Vintage Painting"
        ));
    }

    public void onViewDetail() {
        String selected = auctionListView.getSelectionModel().getSelectedItem();

        if (selected == null) {
            return;
        }

        if (selected.startsWith("Auction 1")) {
            ClientSession.setSelectedAuctionId("MOCK-AUCTION-001");
        } else if (selected.startsWith("Auction 2")) {
            ClientSession.setSelectedAuctionId("MOCK-AUCTION-002");
        } else if (selected.startsWith("Auction 3")) {
            ClientSession.setSelectedAuctionId("MOCK-AUCTION-003");
        }

        SceneNavigator.switchScene("/fxml/AuctionDetail.fxml");
    }

    public void onBackHome() {
        SceneNavigator.switchScene("/fxml/MainLayout.fxml");
    }
}