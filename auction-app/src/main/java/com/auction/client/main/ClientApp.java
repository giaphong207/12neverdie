package com.auction.client.main;

import com.auction.client.network.RealtimeListener;
import com.auction.client.network.ServerConnection;
import com.auction.client.realtime.AuctionEventBus;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.SceneNavigator;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientApp extends Application {

    private static RealtimeListener listener;

    /** Getter cho các Controller lấy listener (chờ response). */
    public static RealtimeListener getListener() {
        return listener;
    }

    @Override
    public void start(Stage stage) {
        try {
            // ① Đăng ký stage cho SceneNavigator để các Controller dùng switchScene()
            SceneNavigator.setStage(stage);

            // ② Load màn Login từ FXML
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/Login.fxml"));
            Scene scene = new Scene(root);
            stage.setTitle("Hệ thống Đấu giá Trực tuyến");
            stage.setScene(scene);

            // ③ Khởi tạo kết nối mạng và Listener chạy ngầm
            initNetwork();

            // ④ Bắt sự kiện khi người dùng tắt cửa sổ
            stage.setOnCloseRequest(event -> {
                System.out.println("Dang dong ung dung...");
                if (listener != null) {
                    listener.stop();
                }
                ServerConnection.getInstance().close();
            });
            stage.show();

        } catch (Exception e) {
            System.err.println("Lỗi khởi động ứng dụng: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initNetwork() {
        try {
            ServerConnection connection = ServerConnection.getInstance();
            connection.connect("localhost", 9999);

            listener = new RealtimeListener(connection.getInputStream(), AuctionEventBus.getInstance());
            Thread listenerThread = new Thread(listener);
            listenerThread.setDaemon(true);
            listenerThread.start();

        } catch (Exception e) {
            System.err.println("Khong the ket noi den Server luc khoi dong: " + e.getMessage());
            AlertUtils.showError("Lỗi Máy Chủ", "Không thể kết nối đến máy chủ. Hãy kiểm tra Server đã chạy chưa.");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}