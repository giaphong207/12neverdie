package com.auction.client.network;

import java.io.ObjectInputStream;

import com.auction.client.realtime.AuctionEventBus;
import com.auction.client.util.AlertUtils;
import com.auction.shared.network.AuctionUpdateEvent;
import com.auction.shared.network.ErrorMessage;

import javafx.application.Platform; 

/**
 * Background thread lắng nghe event từ server
 * Chạy liên tục, đọc object từ stream cho đến khi bị ngắt
 */
public class RealtimeListener implements Runnable {
    
    private final ObjectInputStream inputStream;
    private final AuctionEventBus eventBus;
    private volatile boolean running = true;

    public RealtimeListener(ObjectInputStream inputStream, AuctionEventBus eventBus) {
        this.inputStream = inputStream;
        this.eventBus = eventBus;
    }

    @Override
    public void run() {
        System.out.println("RealtimeListener khoi dong, cho event tu server.");
        
        while (running) {
            try {
                //Đọc object từ server (blocking - dừng chờ đến khi có event)
                Object incoming = inputStream.readObject();
                
                //Xử lý từng loại event khác nhau
                if (incoming instanceof AuctionUpdateEvent event) {
                    System.out.println("Nhan AuctionUpdateEvent: " + event.getAuction().getId());
                    eventBus.publish(event);
                    
                } else if (incoming instanceof ErrorMessage error) {
                    System.err.println("Nhan loi tu server: " + error.getMessage());
                    
                    //Bọc Alert trong Platform.runLater để không bị lỗi UI Thread
                    Platform.runLater(() -> {
                        AlertUtils.showError("Lỗi từ Server", error.getMessage());
                    });
                    
                } else {
                    System.out.println("Nhan object khong xac dinh: " + 
                        (incoming != null ? incoming.getClass().getSimpleName() : "null"));
                }
                
            } catch (java.io.EOFException e) {
                //Server chủ động đóng kết nối
                System.out.println("Server da dong ket noi.");
                running = false;
                Platform.runLater(() -> {
                    AlertUtils.showError("Mất kết nối", "Server đã đóng kết nối.");
                });
                
            } catch (ClassNotFoundException e) {
                System.err.println("Loi deserialize object: " + e.getMessage());
                e.printStackTrace();
                
            } catch (java.io.IOException e) {
                if (running) {  //Nếu cờ running vẫn true nghĩa là lỗi ngoài ý muốn
                    System.err.println("Loi doc stream: " + e.getMessage());
                    running = false;
                    Platform.runLater(() -> {
                        AlertUtils.showError("Lỗi kết nối", "Mất kết nối đột ngột với server.");
                    });
                }
            }
        }
        
        System.out.println("RealtimeListener da dung hoat dong."); //ko dấu chỉ in ra ko hiện trên màn hình
    }

    //Dừng listener một cách an toàn
    public void stop() {
        running = false;
    }
}