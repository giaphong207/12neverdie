package com.auction.client.main;

import com.auction.client.network.ServerMessageListener;
import com.auction.client.network.ServerConnection;
import com.auction.client.realtime.AuctionEventBus;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.SceneNavigator;
import com.auction.client.util.SceneStyler;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientApp extends Application {

    private static ServerMessageListener listener;

    public static ServerMessageListener getListener() {
        return listener;
    }

    @Override
    public void start(Stage stage) {
        try {
            SceneNavigator.setStage(stage);

            Parent root = FXMLLoader.load(getClass().getResource("/fxml/Login.fxml"));
            Scene scene = new Scene(root, 1280, 800);

            // Áp dụng theme Library Bronze cho Scene đầu tiên
            SceneStyler.apply(scene);

            stage.setTitle("AuctionHub — Hệ thống Đấu giá Trực tuyến");
            stage.setScene(scene);
            stage.setMinWidth(1024);
            stage.setMinHeight(720);

            initNetwork();

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

            listener = new ServerMessageListener(connection.getInputStream(), AuctionEventBus.getInstance());
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