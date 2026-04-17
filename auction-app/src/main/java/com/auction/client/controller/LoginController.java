package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.server.service.AuthService;
import com.auction.server.service.DefaultAuthService;
import com.auction.shared.model.User;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    //Khai báo các biến khớp với fx:id bên file FXML
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;

    private final AuthService authService = new DefaultAuthService();

    @FXML
    public void onLoginClicked() {
        String username = txtUsername.getText();
        String password = txtPassword.getText();

        //Kiểm tra rỗng (Validation)
        if (username.trim().isEmpty() || password.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Lỗi", "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!");
            return;
        }

        //Gọi Service để đăng nhập
        User user = authService.login(username, password);
        
        if (user != null) {
            //Nếu thành công -> Lưu vào Session 
            ClientSession.setCurrentUser(user);
            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đăng nhập thành công với quyền " + user.getRole());
            
            //TODO : viết code chuyển màn hình (Router)
            System.out.println("Chuyển sang màn hình của: " + user.getRole());
            
        } else {
            showAlert(Alert.AlertType.ERROR, "Thất bại", "Sai tên đăng nhập hoặc mật khẩu!");
        }
    }

    @FXML
    public void onOpenRegisterClicked() {
        //TODO : Chuyển sang màn hình Register.fxml
        System.out.println("Mở màn hình đăng ký...");
    }

    //Hàm tiện ích để hiện thông báo Pop-up
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
} 