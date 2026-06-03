package com.auction.client.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auction.client.network.ServerConnection;
import com.auction.client.network.ServerMessageListener;
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
    private static final Logger log = LoggerFactory.getLogger(ClientApp.class);
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
                log.info("Đang đóng ứng dụng...");                
                    if (listener != null) {
                    listener.stop();
                }
                ServerConnection.getInstance().close();
            });
            stage.show();

        } catch (Exception e) {
            log.error("Lỗi khởi động ứng dụng", e);            
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
            log.error("Không thể kết nối Server lúc khởi động", e);
            AlertUtils.showError("Lỗi Máy Chủ", "Không thể kết nối đến máy chủ. Hãy kiểm tra Server đã chạy chưa.");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}