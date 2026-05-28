package com.auction.client.network;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.auction.client.realtime.AuctionEventBus;
import com.auction.client.util.AlertUtils;

import com.auction.shared.networkMessage.AuctionEvents.*;
import com.auction.shared.networkMessage.Results.*;
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

    /**
     * Gửi request rồi chờ đúng Result trả về, trong một thao tác có khóa.
     * Vì chỉ một luồng được vào tại một thời điểm (synchronized), không thể
     * xảy ra cảnh 2 controller cùng chờ trên responseQueue và nhặt nhầm Result của nhau.
     *
     * @param request object request cần gửi
     * @param timeoutMs thời gian chờ tối đa (ms); quá hạn coi như server không trả lời
     * @return Result tương ứng, hoặc null nếu hết thời gian chờ
     */
    public synchronized Object sendAndWait(Object request, long timeoutMs)
            throws IOException, InterruptedException {

        ServerConnection.getInstance().send(request);          // (1) gửi đi
        return responseQueue.poll(timeoutMs, TimeUnit.MILLISECONDS); // (2) chờ có giới hạn
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

                } else if (incoming instanceof GetBalanceResult) {
                    System.out.println("Nhan GetBalanceResult -> day vao queue");
                    responseQueue.offer(incoming);

                } else if (incoming instanceof DepositResult) {
                    System.out.println("Nhan DepositResult -> day vao queue");
                    responseQueue.offer(incoming);
                } else if (incoming instanceof SetAutoBidResponse) {
                    System.out.println("Nhan SetAutoBidResponse -> day vao queue");
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