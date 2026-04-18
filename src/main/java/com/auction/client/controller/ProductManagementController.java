package com.auction.client.controller;

import com.auction.client.util.SceneNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class ProductManagementController {

    @FXML
    private TextField productNameField;

    @FXML
    private TextField priceField;

    @FXML
    private Label messageLabel;

    public void initialize() {
        messageLabel.setText("Product Management - shell only");
    }

    public void onAddProduct() {
        String name = productNameField.getText();
        String price = priceField.getText();
        messageLabel.setText("Mock add product: " + name + " - " + price);
    }

    public void onBackHome() {
        SceneNavigator.switchScene("/fxml/MainLayout.fxml");
    }
}