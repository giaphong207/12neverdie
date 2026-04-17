package com.auction.client.controller;

import com.auction.server.service.AuthService;
import com.auction.server.service.DefaultAuthService;
import com.auction.shared.model.Role;
import com.auction.shared.model.User;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class RegisterController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private ComboBox<Role> cbRole;

    private final AuthService authService = new DefaultAuthService();

    @FXML
    public void initialize() {
        //Chỉ SELLER và BIDDER vào hộp thoại chọn vai trò
        cbRole.setItems(FXCollections.observableArrayList(Role.SELLER, Role.BIDDER));
    }

    @FXML
    public void onRegisterClicked() {
        String username = txtUsername.getText();
        String password = txtPassword.getText();
        Role role = cbRole.getValue();

        //Kiểm tra không được để trống bất kỳ ô nào
        if (username.trim().isEmpty() || password.trim().isEmpty() || role == null) {
            showAlert(Alert.AlertType.WARNING, "Lỗi", "Vui lòng nhập đủ thông tin và chọn vai trò!");
            return;
        }

        //Gọi Service để đăng ký
        User newUser = authService.register(username, password, role);
        
        if (newUser != null) {
            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đăng ký thành công! Vui lòng đăng nhập.");
            //TODO: Chuyển về màn hình Login
            System.out.println("Chuyển về màn hình đăng nhập...");
            
        } else {
            showAlert(Alert.AlertType.ERROR, "Thất bại", "Tên đăng nhập đã tồn tại! Vui lòng chọn tên khác.");
        }
    }

    @FXML
    public void onBackToLoginClicked() {
        //TODO: Chuyển về màn hình Login
        System.out.println("Quay lại màn hình đăng nhập...");
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}