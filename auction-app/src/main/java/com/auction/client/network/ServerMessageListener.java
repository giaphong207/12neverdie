package com.auction.client.network;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auction.client.context.ClientSession;
import com.auction.client.realtime.AuctionEventBus;
import com.auction.client.util.AlertUtils;
import com.auction.shared.model.user.User;
import com.auction.shared.networkMessage.AuctionEvents.AuctionEvent;
import com.auction.shared.networkMessage.AuctionEvents.WalletUpdatedEvent;
import com.auction.shared.networkMessage.Results.AddItemResult;
import com.auction.shared.networkMessage.Results.AdminDeleteItemResult;
import com.auction.shared.networkMessage.Results.BidResult;
import com.auction.shared.networkMessage.Results.DepositResult;
import com.auction.shared.networkMessage.Results.ErrorMessage;
import com.auction.shared.networkMessage.Results.GetAdminStatsResult;
import com.auction.shared.networkMessage.Results.GetAllItemsResult;
import com.auction.shared.networkMessage.Results.GetAllUsersResult;
import com.auction.shared.networkMessage.Results.GetBalanceResult;
import com.auction.shared.networkMessage.Results.GetSellerItemsResult;
import com.auction.shared.networkMessage.Results.LoginResult;
import com.auction.shared.networkMessage.Results.RegisterResult;
import com.auction.shared.networkMessage.Results.SetAutoBidResponse;
import com.auction.shared.networkMessage.Results.UpdateItemResult;

import javafx.application.Platform;

/**
 * Background thread lắng nghe event từ server.
 * Đọc object từ stream → phân phối theo loại:
 *  - AuctionUpdateEvent: publish lên EventBus (broadcast cho UI)
 *  - LoginResult / RegisterResult / BidResult: đẩy vào response queue
 *    (LoginController/RegisterController sẽ poll queue này để lấy)
 *  - AddItemResponse / UpdateItemResponse / GetSellerItemsResponse:
 *    đẩy vào queue cho ProductManagementController
 *  - ErrorMessage: hiển thị alert
 */
public class ServerMessageListener implements Runnable {

    private final ObjectInputStream inputStream;
    private final AuctionEventBus eventBus;
    private volatile boolean running = true;

    /** Hàng đợi response trả về cho các controller (Login/Register/Bid/Item). */
    private final BlockingQueue<Object> responseQueue = new LinkedBlockingQueue<>();

    private static final Logger log = LoggerFactory.getLogger(ServerMessageListener.class);
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
        log.info("RealtimeListener khởi động, chờ event từ server.");

        while (running) {
            try {
                Object incoming = inputStream.readObject();

                if (incoming instanceof WalletUpdatedEvent walletEvent) {
                    log.debug("Nhận WalletUpdatedEvent → cập nhật số dư");
                    applyWalletUpdate(walletEvent);

                } else if (incoming instanceof AuctionEvent event) {
                    log.debug("Nhận AuctionEvent: {}", event.getAuction().getId());
                    eventBus.publish(event);

                } else if (incoming instanceof LoginResult) {
                    responseQueue(incoming);
                } else if (incoming instanceof RegisterResult) {
                    responseQueue(incoming);
                } else if (incoming instanceof BidResult) {
                    responseQueue(incoming);
                } else if (incoming instanceof AddItemResult) {
                    responseQueue(incoming);
                } else if (incoming instanceof UpdateItemResult) {
                    responseQueue(incoming);
                } else if (incoming instanceof GetSellerItemsResult) {
                    responseQueue(incoming);
                } else if (incoming instanceof GetBalanceResult) {
                    responseQueue(incoming);
                } else if (incoming instanceof DepositResult) {
                    responseQueue(incoming);
                } else if (incoming instanceof SetAutoBidResponse) {
                    responseQueue(incoming);
                } else if (incoming instanceof GetAllUsersResult) {
                    responseQueue(incoming);
                } else if (incoming instanceof GetAllItemsResult) {
                    responseQueue(incoming);
                } else if (incoming instanceof GetAdminStatsResult) {
                    responseQueue(incoming);
                } else if (incoming instanceof AdminDeleteItemResult) {
                    responseQueue(incoming);

                } else if (incoming instanceof ErrorMessage error) {
                    log.warn("Nhận lỗi từ server: {}", error.message());
                    Platform.runLater(() ->
                            AlertUtils.showError("Lỗi từ Server", error.message()));

                } else {
                    log.warn("Nhận object không xác định: {}",
                            incoming != null ? incoming.getClass().getSimpleName() : "null");
                }

            } catch (java.io.EOFException e) {
                log.info("Server đã đóng kết nối.");
                running = false;
                Platform.runLater(() ->
                        AlertUtils.showError("Mất kết nối", "Server đã đóng kết nối."));

            } catch (ClassNotFoundException e) {
                log.error("Lỗi deserialize object", e);

            } catch (java.io.IOException e) {
                if (running) {
                    log.error("Lỗi đọc stream", e);
                    running = false;
                    Platform.runLater(() ->
                            AlertUtils.showError("Lỗi kết nối", "Mất kết nối đột ngột với server."));
                }
            }
        }

        log.info("RealtimeListener đã dừng hoạt động.");
    }
    /** Log ở mức debug rồi đẩy response vào hàng đợi cho controller poll. */
    private void responseQueue(Object response) {
        log.debug("Nhận {} → đẩy vào queue", response.getClass().getSimpleName());
        responseQueue.offer(response);
    }
    public void stop() {
        running = false;
    }

    /**
     * Cập nhật số dư ví khi phiên vừa thanh toán xong. Chỉ áp dụng nếu event
     * liên quan tới user đang đăng nhập (là winner hoặc seller).
     * Balance là JavaFX property bound vào topbar → phải set trên FX thread.
     */
    private void applyWalletUpdate(WalletUpdatedEvent event) {
        User me = ClientSession.getCurrentUser();
        if (me == null) {
            return;
        }
        String myId = me.getId();
        long newBalance;
        if (myId.equals(event.getWinnerId())) {
            newBalance = event.getWinnerBalance();
        } else if (myId.equals(event.getSellerId())) {
            newBalance = event.getSellerBalance();
        } else {
            return; // event không liên quan tới mình
        }
        Platform.runLater(() -> ClientSession.setBalance(newBalance));
    }

}