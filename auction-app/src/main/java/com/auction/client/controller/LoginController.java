package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.main.ClientApp;
import com.auction.client.network.RealtimeListener;
import com.auction.client.network.ServerConnection;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.SceneStyler;
import com.auction.shared.model.Role;
import com.auction.shared.model.User;
import com.auction.shared.networkMessage.Requests.LoginRequest;
import com.auction.shared.networkMessage.Responses.LoginResponse;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private CheckBox chkRemember;

    @FXML
    public void onLoginClicked(javafx.event.ActionEvent event) {
        String username = txtUsername.getText();
        String password = txtPassword.getText();

        if (username.trim().isEmpty() || password.trim().isEmpty()) {
            AlertUtils.showWarning("Lỗi", "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!");
            return;
        }

        new Thread(() -> {
            try {
                ServerConnection conn = ServerConnection.getInstance();
                conn.send(new LoginRequest(username, password));

                RealtimeListener listener = ClientApp.getListener();
                if (listener == null) {
                    Platform.runLater(() ->
                            AlertUtils.showError("Lỗi", "Listener chưa được khởi tạo"));
                    return;
                }

                Object response = listener.waitForResponse();
                Platform.runLater(() -> handleLoginResponse(response, event));

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() ->
                        AlertUtils.showError("Lỗi mạng", "Không gửi được yêu cầu: " + e.getMessage()));
            }
        }).start();
    }

    private void handleLoginResponse(Object response, javafx.event.ActionEvent event) {
        if (response instanceof LoginResponse loginResp) {
            if (loginResp.success()) {
                User user = loginResp.user();
                ClientSession.setCurrentUser(user);
                AlertUtils.showInfo("Thành công",
                        "Đăng nhập thành công với quyền " + roleVi(user.getRole()));
                navigateByRole(event, user);
            } else {
                AlertUtils.showError("Thất bại", loginResp.message());
            }
        } else {
            AlertUtils.showError("Lỗi", "Phản hồi không hợp lệ: " +
                    (response == null ? "null" : response.getClass().getSimpleName()));
        }
    }

    private String roleVi(Role role) {
        return switch (role) {
            case ADMIN -> "Quản trị viên";
            case SELLER -> "Người bán";
            case BIDDER -> "Người đấu giá";
        };
    }

    private void navigateByRole(javafx.event.ActionEvent event, User user) {
        try {
            String fxmlPath;
            String title;

            if (user.getRole() == Role.SELLER) {
                fxmlPath = "/fxml/SellerDashboard.fxml";
                title = "AuctionHub — Người Bán";
            } else if (user.getRole() == Role.BIDDER) {
                fxmlPath = "/fxml/BidderDashboard.fxml";
                title = "AuctionHub — Người Đấu Giá";
            } else {
                fxmlPath = "/fxml/AdminDashboard.fxml";
                title = "AuctionHub — Quản trị viên";
            }

            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource(fxmlPath));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage = (javafx.stage.Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();

            javafx.scene.Scene scene = new javafx.scene.Scene(root, 1280, 800);
            SceneStyler.apply(scene);

            stage.setScene(scene);
            stage.setTitle(title);
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            AlertUtils.showError("Lỗi hệ thống", "Không tải được FXML: " + e.getMessage());
        }
    }

    @FXML
    public void onOpenRegisterClicked(javafx.event.ActionEvent event) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/Register.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage = (javafx.stage.Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();

            javafx.scene.Scene scene = new javafx.scene.Scene(root, 1280, 800);
            SceneStyler.apply(scene);

            stage.setScene(scene);
            stage.setTitle("AuctionHub — Đăng ký tài khoản");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtils.showError("Lỗi hệ thống", "Không thể mở Register.fxml");
        }
    }
}