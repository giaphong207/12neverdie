package com.auction.server.main;

import com.auction.server.concurrency.AuctionLockManager;
import com.auction.server.DAO.*;
import com.auction.server.handler.ClientHandler;
import com.auction.server.realtime.AuctionSubscriptionManager;
import com.auction.server.realtime.EventBroadcaster;
import com.auction.server.seed.DatabaseSeeder;
import com.auction.server.service.*;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;

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

        // ④ Realtime + Lock
        // Khởi tạo dependencies cần trước
        AuctionLockManager lockManager = new AuctionLockManager();
        AuctionSubscriptionManager subscriptionManager = new AuctionSubscriptionManager();
        EventBroadcaster broadcaster = new EventBroadcaster(subscriptionManager);

        // ⑤ Service layer
        AuctionLifecycleService lifecycleService =
                new DefaultAuctionLifecycleService(auctionDao, broadcaster, lockManager);
        AuctionService auctionService =
                new DefaultAuctionService(auctionDao, lifecycleService, broadcaster);

        // AntiSniping cần Duration
        AntiSnipingService antiSniping = new DefaultAntiSnipingService(
                Duration.ofSeconds(60),   // trigger window
                Duration.ofSeconds(60)    // extension
        );
        AutoBidService autoBidService = new DefaultAutoBidService(autoBidDao);
        AuthService authService = new DefaultAuthService(userDao);

        BidService bidService = new DefaultBidService(
                db, auctionDao, bidDao, lifecycleService,
                lockManager, antiSniping, autoBidService);

        // ⑥ Re-schedule tasks sau restart cho các auction chưa terminal
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
            // PAID, CANCELED → không cần schedule
        }

        // ⑥ Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Shutdown] Đóng scheduler...");
            lifecycleService.shutdown();
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
                        socket, bidService, authService, auctionDao, auctionService, itemDao,
                        subscriptionManager, broadcaster);
                new Thread(handler).start();
            }
        } catch (BindException e) {
            System.err.println();
            System.err.println("================================================================");
            System.err.println(" [Lỗi] Port " + port + " đã bị chiếm — không thể start Server.");
            System.err.println("================================================================");
            System.err.println(" Nguyên nhân thường gặp:");
            System.err.println("   • Bạn đã chạy ServerApp trước đó nhưng chưa dừng hẳn.");
            System.err.println("   • IntelliJ vẫn còn process Java treo nền sau khi Stop.");
            System.err.println();
            System.err.println(" Cách xử lý nhanh (macOS/Linux):");
            System.err.println("   1) Tìm PID đang giữ port:   lsof -i :" + port);
            System.err.println("   2) Kill process đó:         kill -9 <PID>");
            System.err.println("   Hoặc gọn hơn:               pkill -9 -f ServerApp");
            System.err.println();
            System.err.println(" Trên Windows:");
            System.err.println("   netstat -ano | findstr :" + port);
            System.err.println("   taskkill /F /PID <PID>");
            System.err.println("================================================================");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Lỗi Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}