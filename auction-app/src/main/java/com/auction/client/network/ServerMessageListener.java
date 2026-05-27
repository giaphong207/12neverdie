package com.auction.client.network;

import java.io.ObjectInputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.auction.client.realtime.AuctionEventBus;
import com.auction.client.util.AlertUtils;

import com.auction.shared.networkMessage.AuctionEvents.*;
import com.auction.shared.networkMessage.Responses.*;
import javafx.application.Platform;

/**
 * Background thread lắng nghe event từ server.
 * Đọc object từ stream → phân phối theo loại:
 *  - AuctionUpdateEvent: publish lên EventBus (broadcast cho UI)
 *  - LoginResult / RegisterResult / BidResult: đẩy vào response queue
 *    (LoginController/RegisterController sẽ poll queue này để lấy)
 *  - AddItemResponse / UpdateItemResponse / DeleteItemResponse / GetSellerItemsResponse:
 *    đẩy vào queue cho ProductManagementController
 *  - ErrorMessage: hiển thị alert
 */
public class ServerMessageListener implements Runnable {

    private final ObjectInputStream inputStream;
    private final AuctionEventBus eventBus;
    private volatile boolean running = true;

    /** Hàng đợi response trả về cho các controller (Login/Register/Bid/Item). */
    private final BlockingQueue<Object> responseQueue = new LinkedBlockingQueue<>();

    public ServerMessageListener(ObjectInputStream inputStream, AuctionEventBus eventBus) {
        this.inputStream = inputStream;
        this.eventBus = eventBus;
    }

    /** Controller gọi method này để chờ response từ server (blocking). */
    public Object waitForResponse() throws InterruptedException {
        return responseQueue.take();
    }

    @Override
    public void run() {
        System.out.println("RealtimeListener khoi dong, cho event tu server.");

        while (running) {
            try {
                Object incoming = inputStream.readObject();

                if (incoming instanceof AuctionEvent event) {
                    System.out.println("Nhan AuctionUpdateEvent: " + event.getAuction().getId());
                    eventBus.publish(event);

                } else if (incoming instanceof LoginResult) {
                    System.out.println("Nhan LoginResponse -> day vao queue");
                    responseQueue.offer(incoming);

                } else if (incoming instanceof RegisterResult) {
                    System.out.println("Nhan RegisterResponse -> day vao queue");
                    responseQueue.offer(incoming);

                } else if (incoming instanceof BidResult) {
                    System.out.println("Nhan BidResult -> day vao queue");
                    responseQueue.offer(incoming);

                } else if (incoming instanceof AddItemResult) {
                    System.out.println("Nhan AddItemResponse -> day vao queue");
                    responseQueue.offer(incoming);

                } else if (incoming instanceof UpdateItemResult) {
                    System.out.println("Nhan UpdateItemResponse -> day vao queue");
                    responseQueue.offer(incoming);

                } else if (incoming instanceof DeleteItemResult) {
                    System.out.println("Nhan DeleteItemResponse -> day vao queue");
                    responseQueue.offer(incoming);

                } else if (incoming instanceof GetSellerItemsResult) {
                    System.out.println("Nhan GetSellerItemsResponse -> day vao queue");
                    responseQueue.offer(incoming);

                } else if (incoming instanceof ErrorMessage error) {
                    System.err.println("Nhan loi tu server: " + error.message());
                    Platform.runLater(() ->
                            AlertUtils.showError("Lỗi từ Server", error.message()));

                } else {
                    System.out.println("Nhan object khong xac dinh: " +
                            (incoming != null ? incoming.getClass().getSimpleName() : "null"));
                }

            } catch (java.io.EOFException e) {
                System.out.println("Server da dong ket noi.");
                running = false;
                Platform.runLater(() ->
                        AlertUtils.showError("Mất kết nối", "Server đã đóng kết nối."));

            } catch (ClassNotFoundException e) {
                System.err.println("Loi deserialize object: " + e.getMessage());
                e.printStackTrace();

            } catch (java.io.IOException e) {
                if (running) {
                    System.err.println("Loi doc stream: " + e.getMessage());
                    running = false;
                    Platform.runLater(() ->
                            AlertUtils.showError("Lỗi kết nối", "Mất kết nối đột ngột với server."));
                }
            }
        }

        System.out.println("RealtimeListener da dung hoat dong.");
    }

    public void stop() {
        running = false;
    }
}