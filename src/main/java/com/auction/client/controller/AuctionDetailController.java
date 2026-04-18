package com.auction.client.controller;

import com.auction.client.util.SceneNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class AuctionDetailController {

    @FXML
    private Label itemNameLabel;

    @FXML
    private Label currentPriceLabel;

    @FXML
    private TextField bidAmountField;

    @FXML
    private Label statusLabel;

    public void initialize() {
        itemNameLabel.setText("Mock Item: iPhone 15");
        currentPriceLabel.setText("Current Price: 20,000,000");
        statusLabel.setText("Detail screen - tuần 1 chưa bid thật");
    }

    public void onPlaceBid() {
        String bidAmount = bidAmountField.getText();
        statusLabel.setText("Mock bid amount: " + bidAmount);
    }

    public void onBackToList() {
        SceneNavigator.switchScene("/fxml/AuctionList.fxml");
    }

    public void onBackHome() {
        SceneNavigator.switchScene("/fxml/MainLayout.fxml");
    }
}