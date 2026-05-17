package com.auction.client.controller;

import com.auction.client.main.ClientApp;
import com.auction.client.network.RealtimeListener;
import com.auction.client.network.ServerConnection;
import com.auction.client.util.AlertUtils;
import com.auction.shared.model.Role;
import com.auction.shared.network.RegisterRequest;
import com.auction.shared.network.RegisterResponse;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class RegisterController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private ComboBox<Role> cbRole;

    @FXML
    public void initialize() {
        cbRole.setItems(FXCollections.observableArrayList(Role.SELLER, Role.BIDDER));
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

                RealtimeListener listener = ClientApp.getListener();
                if (listener == null) {
                    Platform.runLater(() ->
                            AlertUtils.showError("Lỗi", "Listener chưa được khởi tạo"));
                    return;
                }

                Object response = listener.waitForResponse();
                Platform.runLater(() -> handleRegisterResponse(response, event));

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() ->
                        AlertUtils.showError("Lỗi mạng", "Không gửi được yêu cầu: " + e.getMessage()));
            }
        }).start();
    }

    private void handleRegisterResponse(Object response, javafx.event.ActionEvent event) {
        if (response instanceof RegisterResponse regResp) {
            if (regResp.isSuccess()) {
                AlertUtils.showInfo("Thành công", "Đăng ký thành công! Vui lòng đăng nhập.");
                try {
                    javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
                    javafx.scene.Parent root = loader.load();
                    javafx.stage.Stage stage = (javafx.stage.Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
                    stage.setScene(new javafx.scene.Scene(root));
                    stage.setTitle("Đăng nhập");
                    stage.show();
                } catch (Exception ex) {
                    AlertUtils.showError("Lỗi", "Không mở được màn Login: " + ex.getMessage());
                }
            } else {
                AlertUtils.showError("Thất bại", regResp.getMessage());
            }
        } else {
            AlertUtils.showError("Lỗi", "Phản hồi không hợp lệ");
        }
    }
}