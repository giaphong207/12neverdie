package com.auction.client.controller;

import com.auction.client.util.SceneNavigator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainLayoutController {
    private static final Logger log = LoggerFactory.getLogger(MainLayoutController.class);

    public void initialize() {log.debug("Đã tải MainLayout thành công.");}

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