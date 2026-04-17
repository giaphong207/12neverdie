package com.auction.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        try {
            //Tìm và nạp giao diện từ file FXML
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/Login.fxml"));
            
            //Thiết lập Scene
            Scene scene = new Scene(root);
            
            //Cấu hình cửa sổ
            stage.setTitle("Hệ thống Đấu giá Trực tuyến");
            stage.setScene(scene);
            stage.show(); //Hiện hình
            
        } catch (Exception e) {
            System.err.println("Lỗi khởi động ứng dụng: Không thể tải giao diện đăng nhập.");
            System.err.println("Chi tiết lỗi: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args); //Lệnh kích hoạt JavaFX
    }
}