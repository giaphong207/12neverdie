package com.auction.server.main;

import com.auction.server.concurrency.AuctionLockManager;
import com.auction.server.dao.*;
import com.auction.server.handler.ClientHandler;
import com.auction.server.realtime.AuctionSubscriptionManager;
import com.auction.server.realtime.EventBroadcaster;
import com.auction.server.seed.DatabaseSeeder;
import com.auction.server.service.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerApp {

    public static void main(String[] args) {
        int port = 9999;

        System.out.println("=== HỆ THỐNG ĐẤU GIÁ SERVER ===");

        // ① Init Database (HikariCP)
        Database db = Database.getInstance();

        // ② DAO layer
        UserDao userDao = new JdbcUserDao(db);
        ItemDao itemDao = new JdbcItemDao(db);
        BidDao bidDao = new JdbcBidDao(db);
        AuctionDao auctionDao = new JdbcAuctionDao(db, bidDao);
        AutoBidDao autoBidDao = new JdbcAutoBidDao(db);

        // ③ Seed data nếu DB trống
        new DatabaseSeeder(userDao, itemDao, auctionDao).seedIfEmpty();

        // ④ Service layer
        AuctionLifecycleService lifecycleService = new DefaultAuctionLifecycleService(auctionDao);
        AuctionLockManager lockManager = new AuctionLockManager();
        AntiSnipingService antiSniping = new DefaultAntiSnipingService();
        AutoBidService autoBidService = new DefaultAutoBidService(autoBidDao);
        AuthService authService = new DefaultAuthService(userDao);

        BidService bidService = new DefaultBidService(
                db, auctionDao, bidDao, lifecycleService,
                lockManager, antiSniping, autoBidService);

        // ⑤ Realtime
        AuctionSubscriptionManager subscriptionManager = new AuctionSubscriptionManager();
        EventBroadcaster broadcaster = new EventBroadcaster(subscriptionManager);

        // ⑥ Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Shutdown] Đóng connection pool...");
            db.shutdown();
        }));

        // ⑦ Listen socket
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server đang lắng nghe tại port: " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Client mới kết nối: " + socket.getRemoteSocketAddress());

                ClientHandler handler = new ClientHandler(
                        socket, bidService, authService, auctionDao, itemDao,
                        subscriptionManager, broadcaster);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("Lỗi Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}