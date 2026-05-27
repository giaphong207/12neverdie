package com.auction.client.controller;

import com.auction.client.main.ClientApp;
import com.auction.client.network.ServerMessageListener;
import com.auction.client.network.ServerConnection;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.SceneStyler;

import com.auction.shared.model.user.Role;
import com.auction.shared.networkMessage.Requests.*;
import com.auction.shared.networkMessage.Results.*;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

public class RegisterController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private ComboBox<Role> cbRole;

    @FXML
    public void initialize() {
        cbRole.setItems(FXCollections.observableArrayList(Role.BIDDER, Role.SELLER));

        // Hiển thị tiếng Việt thay vì BIDDER/SELLER trong ComboBox
        cbRole.setConverter(new StringConverter<>() {
            @Override
            public String toString(Role role) {
                if (role == null) return "";
                return switch (role) {
                    case BIDDER -> "Người đấu giá";
                    case SELLER -> "Người bán";
                    case ADMIN -> "Quản trị viên";
                };
            }

            @Override
            public Role fromString(String s) {
                return null;
            }
        });
    }

    @FXML
    public void onRegisterClicked(javafx.event.ActionEvent event) {
        String username = txtUsername.getText();
        String password = txtPassword.getText();
        Role role = cbRole.getValue();

        if (username.trim().isEmpty() || password.trim().isEmpty() || role == null) {
            AlertUtils.showWarning("Lỗi", "Vui lòng nhập đủ thông tin và chọn vai trò!");
            return;
        }

        new Thread(() -> {
            try {
                ServerConnection conn = ServerConnection.getInstance();
                conn.send(new RegisterRequest(username, password, role));

                ServerMessageListener listener = ClientApp.getListener();
                if (listener == null) {
                    Platform.runLater(() ->
                            AlertUtils.showError("Lỗi", "Listener chưa được khởi tạo"));
                    return;
                }

                Object response = listener.waitForResponse();
                Platform.runLater(() -> handleRegisterResult(response, event));

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() ->
                        AlertUtils.showError("Lỗi mạng", "Không gửi được yêu cầu: " + e.getMessage()));
            }
        }).start();
    }

    private void handleRegisterResult(Object response, javafx.event.ActionEvent event) {
        if (response instanceof RegisterResult result) {
            switch (result) {
                case RegisterResult.Success s -> {
                    AlertUtils.showInfo("Thành công", "Đăng ký thành công! Vui lòng đăng nhập.");
                    openLoginScreen(event);
                }
                case RegisterResult.Failure f -> {
                    AlertUtils.showError("Thất bại", f.reason());
                }
            }
        } else {
            AlertUtils.showError("Lỗi", "Phản hồi không hợp lệ");
        }
    }

    @FXML
    public void onBackToLoginClicked(javafx.event.ActionEvent event) {
        openLoginScreen(event);
    }

    private void openLoginScreen(javafx.event.ActionEvent event) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage = (javafx.stage.Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();

            javafx.scene.Scene scene = new javafx.scene.Scene(root, 1280, 800);
            SceneStyler.apply(scene);

            stage.setScene(scene);
            stage.setTitle("AuctionHub — Đăng nhập");
            stage.show();
        } catch (Exception ex) {
            ex.printStackTrace();
            AlertUtils.showError("Lỗi", "Không mở được màn Login: " + ex.getMessage());
        }
    }
}