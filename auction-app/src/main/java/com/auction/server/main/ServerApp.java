package com.auction.server.main;

import com.auction.server.concurrency.AuctionLockManager;
import com.auction.server.dao.*;
import com.auction.server.handler.ClientHandler;
import com.auction.server.realtime.AuctionEnricher;
import com.auction.server.realtime.AuctionSubscriptionManager;
import com.auction.server.realtime.EventBroadcaster;
import com.auction.server.seed.DatabaseSeeder;
import com.auction.server.service.*;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;
import com.auction.shared.config.AppConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerApp {
    private static final Logger log = LoggerFactory.getLogger(ServerApp.class);

    private static void rescheduleUnfinishedAuctions(AuctionDao auctionDao,
                                                     AuctionLifecycleService lifecycleService) {
        for (Auction a : auctionDao.findAll()) {
            AuctionStatus s = a.getStatus();
            if (s == AuctionStatus.OPEN) {
                lifecycleService.scheduleStart(a);
                lifecycleService.scheduleClose(a);
            } else if (s == AuctionStatus.RUNNING) {
                lifecycleService.scheduleClose(a);
            } else if (s == AuctionStatus.FINISHED) {
                lifecycleService.schedulePaymentTimeout(a);
            }
        }
    }

    private static void registerShutdownHook(AuctionLifecycleService lifecycleService,
                                             Database db) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("[Shutdown] Đóng scheduler...");
            lifecycleService.shutdown();
            log.info("[Shutdown] Đóng connection pool...");
            db.shutdown();
        }));
    }

    public static void main(String[] args) {
        int port = AppConfig.SERVER_PORT;

        log.info("=== HỆ THỐNG ĐẤU GIÁ SERVER ===");

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

        // ④ Realtime + Lock
        // Khởi tạo dependencies cần trước
        AuctionLockManager lockManager = new AuctionLockManager();
        AuctionSubscriptionManager subscriptionManager = new AuctionSubscriptionManager();
        AuctionEnricher enricher = new AuctionEnricher(itemDao, userDao);
        EventBroadcaster broadcaster = new EventBroadcaster(subscriptionManager, enricher);

        // ⑤ Service layer
        WalletService walletService = new DefaultWalletService(userDao);
        AuctionLifecycleService lifecycleService =
                new DefaultAuctionLifecycleService(auctionDao, broadcaster, lockManager, walletService);
        AuctionService auctionService =
                new DefaultAuctionService(auctionDao, lifecycleService, broadcaster);
        ItemService itemService = new DefaultItemService(itemDao);
        // AntiSniping cần Duration
        AntiSnipingService antiSniping = new DefaultAntiSnipingService(
                Duration.ofSeconds(AppConfig.ANTI_SNIPING_TRIGGER_SECONDS),
                Duration.ofSeconds(AppConfig.ANTI_SNIPING_EXTENSION_SECONDS)
        );
        AutoBidService autoBidService = new DefaultAutoBidService(autoBidDao,lockManager);
        AuthService authService = new DefaultAuthService(userDao);

        BidService bidService = new DefaultBidService(
                db, auctionDao, bidDao, userDao, lifecycleService,
                lockManager, antiSniping, autoBidService);

        // ⑥ Re-schedule tasks sau restart cho các auction chưa terminal
        rescheduleUnfinishedAuctions(auctionDao, lifecycleService);

        // ⑦ Shutdown hook
        registerShutdownHook(lifecycleService, db);

        // ⑦ Listen socket
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("Server đang lắng nghe tại port: {}", port);

            while (true) {
                Socket socket = serverSocket.accept();
                log.info("Client mới kết nối: {}", socket.getRemoteSocketAddress());

                ClientHandler handler = new ClientHandler(
                        socket, bidService, authService, walletService,
                        auctionService, itemService, autoBidService,
                        subscriptionManager, broadcaster, enricher);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            log.error("Lỗi Server", e);
        }
    }
}