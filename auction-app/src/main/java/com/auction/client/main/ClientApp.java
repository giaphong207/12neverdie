package com.auction.client.main;

import com.auction.client.network.RealtimeListener;
import com.auction.client.network.ServerConnection;
import com.auction.client.realtime.AuctionEventBus;
import com.auction.client.util.AlertUtils;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientApp extends Application {

    private RealtimeListener listener; //thêm biến listener để có thể điều khiển luồng chạy ngầm

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

            //Khởi động kết nối mạng và Listener chạy ngầm
            initNetwork();

            //Bắt sự kiện khi người dùng bấm dấu X tắt cửa sổ
            stage.setOnCloseRequest(event -> {
                System.out.println("Dang dong ung dung...");
                if (listener != null) {
                    listener.stop(); //dừng luồng nghe ngóng
                }
                ServerConnection.getInstance().close(); //cắt đứt kết nối socket
            });
            stage.show(); //Hiện hình
            
        } catch (Exception e) {
            System.err.println("Lỗi khởi động ứng dụng: Không thể tải giao diện đăng nhập.");
            System.err.println("Chi tiết lỗi: " + e.getMessage());
            e.printStackTrace();
        }
    }

    //Hàm phụ trợ: Khởi tạo kết nối đến Server
    private void initNetwork() {
        try {
            ServerConnection connection = ServerConnection.getInstance();
            //Kết nối tới Server (Đổi "localhost" thành IP thật nếu chạy trên nhiều máy)
            connection.connect("localhost", 9999);

            //Cắm tai nghe (Listener) vào kết nối và ném cho EventBus
            listener = new RealtimeListener(connection.getInputStream(), AuctionEventBus.getInstance());
            
            //Khởi chạy tai nghe trên một luồng phụ (Background thread)
            Thread listenerThread = new Thread(listener);
            listenerThread.setDaemon(true); //Đảm bảo luồng này chết theo khi tắt app
            listenerThread.start();

        } catch (Exception e) {
            System.err.println("Khong the ket noi den Server luc khoi dong.");
            AlertUtils.showError("Lỗi Máy Chủ", "Không thể kết nối đến máy chủ. Vui lòng kiểm tra mạng hoặc thử lại sau!");
        }
    }

    public static void main(String[] args) {
        launch(args); //Lệnh kích hoạt JavaFX
    }
}