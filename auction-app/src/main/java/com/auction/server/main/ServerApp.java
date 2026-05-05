package com.auction.server.main;

import com.auction.server.concurrency.AuctionLockManager;
import com.auction.server.dao.FileAuctionDao;
import com.auction.server.handler.ClientHandler;
import com.auction.server.realtime.AuctionSubscriptionManager;
import com.auction.server.realtime.EventBroadcaster;
import com.auction.server.service.BidService;
import com.auction.server.service.DefaultAuctionLifecycleService;
import com.auction.server.service.DefaultBidService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerApp {

    public static void main(String[] args) {
        int port = 9999;

        FileAuctionDao auctionDao = new FileAuctionDao();
        DefaultAuctionLifecycleService lifecycleService = new DefaultAuctionLifecycleService(auctionDao);
        AuctionLockManager lockManager = new AuctionLockManager();
        BidService bidService = new DefaultBidService(auctionDao, lifecycleService, lockManager);

        AuctionSubscriptionManager subscriptionManager = new AuctionSubscriptionManager();
        EventBroadcaster broadcaster = new EventBroadcaster(subscriptionManager);

        System.out.println("=== HỆ THỐNG ĐẤU GIÁ SERVER ĐANG KHỞI ĐỘNG ===");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server đang lắng nghe kết nối tại port: " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Có Client mới kết nối: " + socket.getRemoteSocketAddress());

                ClientHandler handler = new ClientHandler(socket, bidService, subscriptionManager, broadcaster);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("Lỗi Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}