package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.server.service.AuthService;
import com.auction.server.service.DefaultAuthService;
import com.auction.shared.model.Role;
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
    public void onLoginClicked(javafx.event.ActionEvent event) {
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
            
            //viết code chuyển màn hình (Router)
            try {
                String fxmlPath = "";
                String title = "";
                
                //Kiểm tra Quyền để chỉ định đúng file giao diện
                if (user.getRole() == Role.SELLER) {
                    fxmlPath = "/fxml/SellerDashboard.fxml";
                    title = "Hệ thống Đấu giá - Người Bán";
                } else if (user.getRole() == Role.BIDDER) {
                    fxmlPath = "/fxml/BidderDashboard.fxml";
                    title = "Hệ thống Đấu giá - Người Mua";
                }

                //Tải file giao diện tương ứng
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource(fxmlPath));
                javafx.scene.Parent root = loader.load();
                
                //Lấy cửa sổ hiện tại và chuyển cảnh
                javafx.stage.Stage stage = (javafx.stage.Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
                stage.setScene(new javafx.scene.Scene(root));
                stage.setTitle(title);
                stage.show();
                
            } catch (Exception e) {
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Lỗi hệ thống", "Không thể mở màn hình chính! Vui lòng kiểm tra lại file FXML.");
            }

            System.out.println("Chuyển sang màn hình của: " + user.getRole());
            
        } else {
            showAlert(Alert.AlertType.ERROR, "Thất bại", "Sai tên đăng nhập hoặc mật khẩu!");
        }
    }

    @FXML
    public void onOpenRegisterClicked(javafx.event.ActionEvent event) { //javafx.event.ActionEvent có sẵn của thư viện JavaFX.
        //Chuyển sang màn hình Register.fxml
        try {
            //Tải file giao diện Đăng ký (Đảm bảo đúng đường dẫn /fxml/Register.fxml)
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/Register.fxml"));
            javafx.scene.Parent root = loader.load();
            
            //Lấy cửa sổ (Stage) hiện tại từ sự kiện bấm nút
            javafx.stage.Stage stage = (javafx.stage.Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            
            //Gắn giao diện mới vào cửa sổ và đổi tiêu đề
            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            stage.setScene(scene);
            stage.setTitle("Đăng ký tài khoản");
            stage.show();
            
        } catch (Exception e) {
            e.printStackTrace(); //In lỗi ra terminal để dễ sửa nếu sai đường dẫn
            showAlert(Alert.AlertType.ERROR, "Lỗi hệ thống", "Không thể mở màn hình đăng ký. Vui lòng kiểm tra lại file Register.fxml");
        }
        System.out.println("Mở màn hình đăng ký..."); //chỉ hiện thị bên trong người dùng ko thấy dùng để Debug
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