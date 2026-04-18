package com.auction.client.controller;

import com.auction.client.util.SceneNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label statusLabel;

    public void initialize() {
        statusLabel.setText("Login screen - tuần 1 chưa xử lý thật");
    }

    public void onLogin() {
        String username = usernameField.getText();
        statusLabel.setText("Bạn vừa bấm Login với username: " + username);
    }

    public void onBackHome() {
        SceneNavigator.switchScene("/fxml/MainLayout.fxml");
    }
}