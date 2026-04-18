package com.auction.client.controller;

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
        SceneNavigator.switchScene("/fxml/AuctionDetail.fxml");
    }

    public void onBackHome() {
        SceneNavigator.switchScene("/fxml/MainLayout.fxml");
    }
}