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
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
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

        // ① Init Database (HikariCP) + smoke test ngay để fail-fast khi MySQL down
        // Catch Exception rộng vì HikariCP ném PoolInitializationException (RuntimeException)
        // ngay trong constructor khi không kết nối được — không phải SQLException tử tế.
        Database db;
        try {
            db = Database.getInstance();
            try (Connection probe = db.getConnection()) {
                // chỉ cần lấy được 1 connection là OK
            }
        } catch (Exception e) {
            printDatabaseDownHelp(e);
            System.exit(1);
            return; // unreachable nhưng giúp compiler hiểu db is assigned
        }

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
        } catch (BindException e) {
            printPortInUseHelp(port);
            System.exit(1);
        } catch (IOException e) {
            log.error("Lỗi Server", e);
        }
    }

    private static void printPortInUseHelp(int port) {
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
    }

    private static void printDatabaseDownHelp(Exception e) {
        System.err.println();
        System.err.println("================================================================");
        System.err.println(" [Lỗi] Không kết nối được tới MySQL — không thể start Server.");
        System.err.println("================================================================");
        System.err.println(" Chi tiết: " + e.getMessage());
        System.err.println();
        System.err.println(" Kiểm tra theo thứ tự:");
        System.err.println();
        System.err.println(" 1) MySQL đang chạy chưa?");
        System.err.println("      macOS (Homebrew):   brew services start mysql@8.4");
        System.err.println("      Linux:              sudo systemctl start mysql");
        System.err.println("      Windows:            khởi động dịch vụ MySQL trong Services");
        System.err.println("      Kiểm tra:           lsof -i :3306   (phải có mysqld LISTEN)");
        System.err.println();
        System.err.println(" 2) Đúng user / password / database chưa?");
        System.err.println("      Kiểm tra file:      src/main/resources/db.properties");
        System.err.println("      Test thủ công:      mysql -u auction_user -p auction_db");
        System.err.println();
        System.err.println(" 3) DB đã có cột `balance` chưa? (Nếu DB cũ trước feature ví)");
        System.err.println("      mysql -u auction_user -p auction_db \\");
        System.err.println("        < auction-app/sql/migration_001_balance.sql");
        System.err.println("================================================================");
    }
}