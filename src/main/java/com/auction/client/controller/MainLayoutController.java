package com.auction.client.controller;

import com.auction.client.util.SceneNavigator;

public class MainLayoutController {

    public void initialize() {
        System.out.println("MainLayout loaded successfully.");
    }

    public void onOpenLogin() {
        SceneNavigator.switchScene("/fxml/Login.fxml");
    }

    public void onOpenProductManagement() {
        SceneNavigator.switchScene("/fxml/ProductManagement.fxml");
    }

    public void onOpenAuctionList() {
        SceneNavigator.switchScene("/fxml/AuctionList.fxml");
    }

    public void onOpenAuctionDetail() {
        SceneNavigator.switchScene("/fxml/AuctionDetail.fxml");
    }
}