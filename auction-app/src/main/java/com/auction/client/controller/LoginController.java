package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.util.*;
import com.auction.shared.factory.UserFactory;
import com.auction.shared.model.user.Bidder;
import com.auction.shared.model.user.Seller;
import com.auction.shared.model.user.User;
import com.auction.shared.networkMessage.Requests.*;
import com.auction.shared.networkMessage.Results.*;

import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;

    @FXML
    public void onLoginClicked(javafx.event.ActionEvent event) {
        String username = txtUsername.getText();
        String password = txtPassword.getText();

        if (username.trim().isEmpty() || password.trim().isEmpty()) {
            AlertUtils.showWarning("Lỗi", "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!");
            return;
        }

        RequestExecutor.send(
                new LoginRequest(username, password),
                response -> handleLoginResult(response, event),
                error -> AlertUtils.showError("Đăng nhập thất bại", error)
        );
    }

    private void handleLoginResult(Object response, javafx.event.ActionEvent event) {
        if (response instanceof LoginResult result) {
            switch (result) {
                case LoginResult.Success s -> {
                    User user = s.user();
                    ClientSession.setCurrentUser(user);
                    AlertUtils.showInfo("Thành công",
                            "Đăng nhập thành công với quyền " + EnumFormatter.roleVi(UserFactory.toRole(user)));
                    navigateByRole(user);
                }
                case LoginResult.Failure f -> {
                    AlertUtils.showError("Thất bại", f.reason());
                }
            }
        } else {
            AlertUtils.showError("Lỗi", "Phản hồi không hợp lệ: " +
                    (response == null ? "null" : response.getClass().getSimpleName()));
        }
    }

    private void navigateByRole(User user) {
        String fxmlPath;
        if (user instanceof Seller) {
            fxmlPath = "/fxml/SellerDashboard.fxml";
        } else if (user instanceof Bidder) {
            fxmlPath = "/fxml/BidderDashboard.fxml";
        } else {
            fxmlPath = "/fxml/AdminDashboard.fxml";
        }
        SceneNavigator.switchScene(fxmlPath);
    }

    @FXML
    public void onOpenRegisterClicked() {
        SceneNavigator.switchScene("/fxml/Register.fxml");
    }
}