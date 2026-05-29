package com.auction.client.controller;

import com.auction.client.util.AlertUtils;
import com.auction.client.util.RequestExecutor;
import com.auction.client.util.SceneNavigator;

import com.auction.shared.model.user.Role;
import com.auction.shared.networkMessage.Requests.*;
import com.auction.shared.networkMessage.Results.*;

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
    public void onRegisterClicked() {
        String username = txtUsername.getText();
        String password = txtPassword.getText();
        Role role = cbRole.getValue();

        if (username.trim().isEmpty() || password.trim().isEmpty() || role == null) {
            AlertUtils.showWarning("Lỗi", "Vui lòng nhập đủ thông tin và chọn vai trò!");
            return;
        }

        RequestExecutor.send(
                new RegisterRequest(username, password, role),
                this::handleRegisterResult,
                error -> AlertUtils.showError("Đăng ký thất bại", error)
        );
    }

    private void handleRegisterResult(Object response) {
        if (response instanceof RegisterResult result) {
            switch (result) {
                case RegisterResult.Success s -> {
                    AlertUtils.showInfo("Thành công", "Đăng ký thành công! Vui lòng đăng nhập.");
                    openLoginScreen();
                }
                case RegisterResult.Failure f -> AlertUtils.showError("Thất bại", f.reason());
            }
        } else {
            AlertUtils.showError("Lỗi", "Phản hồi không hợp lệ");
        }
    }

    @FXML
    public void onBackToLoginClicked(javafx.event.ActionEvent event) {
        openLoginScreen();
    }

    private void openLoginScreen() {
        SceneNavigator.switchScene("/fxml/Login.fxml");
    }
}