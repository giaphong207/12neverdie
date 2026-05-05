package com.auction.server.handler;

import com.auction.server.realtime.AuctionSubscriptionManager;
import com.auction.server.realtime.EventBroadcaster;
import com.auction.server.service.BidService;
import com.auction.shared.exception.AppException;
import com.auction.shared.model.Auction;
import com.auction.shared.network.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final BidService bidService;
    private final AuctionSubscriptionManager subscriptionManager;
    private final EventBroadcaster broadcaster;

    private ObjectOutputStream out;
    private ObjectInputStream in;

    public ClientHandler(Socket socket, BidService bidService,
                         AuctionSubscriptionManager subscriptionManager, EventBroadcaster broadcaster) {
        this.socket = socket;
        this.bidService = bidService;
        this.subscriptionManager = subscriptionManager;
        this.broadcaster = broadcaster;
    }

    @Override
    public void run() {
        try {
            // Phải khởi tạo OutputStream trước InputStream để tránh deadlock Socket
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            while (!socket.isClosed()) {
                Object incoming = in.readObject();

                if (incoming instanceof SubscribeAuctionListRequest) {
                    subscriptionManager.subscribeList(this);
                } else if (incoming instanceof SubscribeAuctionRequest) {
                    SubscribeAuctionRequest request = (SubscribeAuctionRequest) incoming;
                    subscriptionManager.subscribeAuction(request.getAuctionId(), this);
                } else if (incoming instanceof BidRequest) {
                    handleBidRequest((BidRequest) incoming);
                }
            }
        } catch (Exception e) {
            System.out.println("Client ngắt kết nối: " + socket.getRemoteSocketAddress());
        } finally {
            cleanUp();
        }
    }

    private void handleBidRequest(BidRequest request) {
        try {
            // Gọi BidService (TV4 đã gài lock concurrency ở trong hàm này)
            Auction updatedAuction = bidService.placeBid(
                    request.getAuctionId(),
                    request.getBidderId(),
                    request.getAmount()
            );

            send(new BidResponse(true, "Đặt giá thành công!", updatedAuction));

            broadcaster.broadcastAuctionUpdate(updatedAuction);

        } catch (AppException ex) {

            send(new BidResponse(false, ex.getMessage(), null));
        } catch (Exception ex) {
            send(new ErrorMessage("Lỗi server khi xử lý bid: " + ex.getMessage()));
            ex.printStackTrace();
        }
    }

    // Hàm send phải có synchronized để tránh đụng độ khi vừa Broadcast vừa phản hồi cá nhân
    public synchronized void send(Object message) {
        try {
            if (out != null && !socket.isClosed()) {
                out.writeObject(message);
                out.reset();
                out.flush();
            }
        } catch (IOException e) {
            cleanUp();
        }
    }

    private void cleanUp() {
        subscriptionManager.remove(this);
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            // Ignore
        }
    }
}