package com.auction.support;

import com.auction.server.concurrency.AuctionLockManager;
import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.AutoBidDao;
import com.auction.server.dao.BidDao;
import com.auction.server.dao.Database;
import com.auction.server.dao.ItemDao;
import com.auction.server.dao.UserDao;
import com.auction.server.handler.ClientHandler;
import com.auction.server.realtime.AuctionEnricher;
import com.auction.server.realtime.AuctionSubscriptionManager;
import com.auction.server.realtime.EventBroadcaster;
import com.auction.server.service.AntiSnipingService;
import com.auction.server.service.AuctionLifecycleService;
import com.auction.server.service.AuctionService;
import com.auction.server.service.AuthService;
import com.auction.server.service.AutoBidService;
import com.auction.server.service.BidService;
import com.auction.server.service.DefaultAntiSnipingService;
import com.auction.server.service.DefaultAuctionLifecycleService;
import com.auction.server.service.DefaultAuctionService;
import com.auction.server.service.DefaultAuthService;
import com.auction.server.service.DefaultAutoBidService;
import com.auction.server.service.DefaultBidService;
import com.auction.server.service.DefaultItemService;
import com.auction.server.service.DefaultWalletService;
import com.auction.server.service.ItemService;
import com.auction.server.service.WalletService;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.bid.AutoBidConfig;
import com.auction.shared.model.bid.Bid;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.user.User;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Test harness: 1 server thật chạy trên port ngẫu nhiên, dùng cho test E2E qua socket.
 *
 * <p>Wiring tương tự ServerApp.main nhưng:
 * <ul>
 *   <li>Port 0 (= OS tự cấp port trống)</li>
 *   <li>DAO dùng fake in-memory (không cần MySQL)</li>
 *   <li>Database singleton vẫn được dùng (Connection lấy từ H2 test profile —
 *       Connection được pass qua DAO nhưng fake DAO không sử dụng nó)</li>
 *   <li>Có method seedXxx để chèn dữ liệu mẫu trước khi test</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * server = new EmbeddedTestServer();
 * server.start();
 * int port = server.getPort();
 * // ... client connect đến localhost:port
 * server.stop();
 * </pre>
 */
public final class EmbeddedTestServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final ExecutorService acceptPool;
    private final ExecutorService clientPool;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Fake DAOs — public để test có thể inspect/seed
    public final FakeUserDao userDao = new FakeUserDao();
    public final FakeItemDao itemDao = new FakeItemDao();
    public final FakeBidDao bidDao = new FakeBidDao();
    public final FakeAuctionDao auctionDao = new FakeAuctionDao();
    public final FakeAutoBidDao autoBidDao = new FakeAutoBidDao();

    // Services
    public final AuctionLockManager lockManager = new AuctionLockManager();
    public final AuctionSubscriptionManager subscriptionManager = new AuctionSubscriptionManager();
    public final AuctionEnricher enricher;
    public final EventBroadcaster broadcaster;
    public final WalletService walletService;
    public final AuctionLifecycleService lifecycleService;
    public final AuctionService auctionService;
    public final ItemService itemService;
    public final AntiSnipingService antiSnipingService;
    public final AutoBidService autoBidService;
    public final AuthService authService;
    public final BidService bidService;

    public EmbeddedTestServer() throws IOException {
        this(Duration.ofSeconds(60), Duration.ofSeconds(60));
    }

    public EmbeddedTestServer(Duration antiSnipingWindow, Duration antiSnipingExtension) throws IOException {
        // ServerSocket trên port 0 → OS cấp port trống
        this.serverSocket = new ServerSocket(0);
        this.acceptPool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "embedded-server-accept");
            t.setDaemon(true);
            return t;
        });
        this.clientPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "embedded-server-client-" + r.hashCode());
            t.setDaemon(true);
            return t;
        });

        // Wire services
        Database db = Database.getInstance();    // H2 in-memory qua test/resources/db.properties

        this.enricher = new AuctionEnricher(itemDao, userDao);
        this.broadcaster = new EventBroadcaster(subscriptionManager, enricher);
        this.walletService = new DefaultWalletService(userDao);
        this.lifecycleService = new DefaultAuctionLifecycleService(
                auctionDao, broadcaster, lockManager, walletService);
        this.auctionService = new DefaultAuctionService(auctionDao, lifecycleService, broadcaster);
        this.itemService = new DefaultItemService(itemDao, auctionDao, lifecycleService);
        this.antiSnipingService = new DefaultAntiSnipingService(
                antiSnipingWindow, antiSnipingExtension);
        this.autoBidService = new DefaultAutoBidService(autoBidDao, lockManager);
        this.authService = new DefaultAuthService(userDao);
        this.bidService = new DefaultBidService(
                db, auctionDao, bidDao, userDao,
                lifecycleService, lockManager,
                antiSnipingService, autoBidService);
    }

    /** Start accept loop trên thread nền. */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Server đã chạy");
        }
        acceptPool.submit(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running.get() && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(
                        socket, bidService, authService, walletService,
                        auctionService, itemService, autoBidService,
                        subscriptionManager, broadcaster, enricher, lifecycleService);
                clientPool.submit(handler);
            } catch (IOException e) {
                // serverSocket bị close khi stop() → thoát loop bình thường
                if (running.get()) {
                    System.err.println("[EmbeddedTestServer] Accept error: " + e.getMessage());
                }
            }
        }
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        try { serverSocket.close(); } catch (IOException ignore) {}
        lifecycleService.shutdown();
        acceptPool.shutdownNow();
        clientPool.shutdownNow();
    }

    @Override public void close() { stop(); }

    // ════════════════════════════════════════════════════════════
    // FAKE DAOS (in-memory, ignore Connection)
    // ════════════════════════════════════════════════════════════

    public static class FakeUserDao implements UserDao {
        public final Map<String, User> store = new ConcurrentHashMap<>();

        @Override public List<User> findAll() { return new ArrayList<>(store.values()); }
        @Override public Optional<User> findByUsername(String username) {
            if (username == null) return Optional.empty();
            return store.values().stream()
                    .filter(u -> u.getUsername().equals(username))
                    .findFirst();
        }
        @Override public Optional<User> findById(String id) {
            return id == null ? Optional.empty() : Optional.ofNullable(store.get(id));
        }
        @Override public void save(User user) { store.put(user.getId(), user); }
        @Override public long updateBalance(String userId, long newBalance) {
            User u = store.get(userId);
            if (u != null) u.setBalance(newBalance);
            return newBalance;
        }
        @Override public synchronized long addBalance(String userId, long delta) {
            User u = store.get(userId);
            if (u == null) return 0;
            u.setBalance(u.getBalance() + delta);
            return u.getBalance();
        }
        @Override public synchronized boolean transfer(String fromId, String toId, long amount) {
            User from = store.get(fromId); User to = store.get(toId);
            if (from == null || to == null) return false;
            if (from.getBalance() < amount) return false;
            from.setBalance(from.getBalance() - amount);
            to.setBalance(to.getBalance() + amount);
            return true;
        }
    }

    public static class FakeItemDao implements ItemDao {
        public final Map<String, Item> store = new ConcurrentHashMap<>();

        @Override public List<Item> findAll() { return new ArrayList<>(store.values()); }
        @Override public Optional<Item> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }
        @Override public List<Item> findBySellerId(String sellerId) {
            return store.values().stream()
                    .filter(i -> i.getSellerId().equals(sellerId))
                    .collect(Collectors.toList());
        }
        @Override public void save(Item item) { store.put(item.getId(), item); }
        @Override public void deleteById(String id) { store.remove(id); }
    }

    public static class FakeBidDao implements BidDao {
        public final List<Bid> saved = Collections.synchronizedList(new ArrayList<>());

        @Override public List<Bid> findByAuctionId(String auctionId) {
            return saved.stream()
                    .filter(b -> b.getAuctionId().equals(auctionId))
                    .toList();
        }
        @Override public void save(Bid bid) { saved.add(bid); }
        @Override public void save(Connection conn, Bid bid) { saved.add(bid); }
        @Override public int countByAuctionId(String auctionId) {
            return (int) saved.stream()
                    .filter(b -> b.getAuctionId().equals(auctionId))
                    .count();
        }
    }

    public static class FakeAuctionDao implements AuctionDao {
        public final Map<String, Auction> store = new ConcurrentHashMap<>();

        @Override public List<Auction> findAll() { return new ArrayList<>(store.values()); }
        @Override public Optional<Auction> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }
        @Override public void save(Auction a) { store.put(a.getId(), a); }
        @Override public void update(Connection conn, Auction a) { store.put(a.getId(), a); }
        @Override public void deleteById(String id) { store.remove(id); }
    }

    public static class FakeAutoBidDao implements AutoBidDao {
        public final Map<String, AutoBidConfig> store = new ConcurrentHashMap<>();

        @Override public void save(AutoBidConfig cfg) { store.put(cfg.getId(), cfg); }
        @Override public Optional<AutoBidConfig> findByAuctionIdAndBidderId(
                String auctionId, String bidderId) {
            return store.values().stream()
                    .filter(c -> c.getAuctionId().equals(auctionId)
                            && c.getBidderId().equals(bidderId))
                    .findFirst();
        }
        @Override public List<AutoBidConfig> findByAuctionId(String auctionId) {
            return store.values().stream()
                    .filter(c -> c.getAuctionId().equals(auctionId))
                    .collect(Collectors.toList());
        }
        @Override public void deleteById(String id) { store.remove(id); }
        @Override public List<AutoBidConfig> findAll() { return new ArrayList<>(store.values()); }
    }
}
