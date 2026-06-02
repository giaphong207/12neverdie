package com.auction.integration;

import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;
import com.auction.shared.model.user.Bidder;
import com.auction.shared.model.user.Role;
import com.auction.shared.model.user.Seller;
import com.auction.shared.model.user.User;
import com.auction.shared.networkMessage.Requests.BidRequest;
import com.auction.shared.networkMessage.Requests.LoginRequest;
import com.auction.shared.networkMessage.Requests.RegisterRequest;
import com.auction.shared.networkMessage.Results.BidResult;
import com.auction.shared.networkMessage.Results.LoginResult;
import com.auction.shared.networkMessage.Results.RegisterResult;
import com.auction.support.EmbeddedTestServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SYSTEM TEST - end-to-end qua TCP Socket thật")
class SystemEndToEndTest {

    private EmbeddedTestServer server;
    private static final String SELLER_ID = "u-seller-e2e";
    private static final String BIDDER_ID = "u-bidder-e2e";
    private static final String AUCTION_ID = "auction-e2e-1";

    @BeforeEach
    void setUp() throws IOException {
        server = new EmbeddedTestServer();
        server.userDao.save(new Seller(SELLER_ID, "seller_e2e",
                BCrypt.hashpw("pwd", BCrypt.gensalt(4))));
        server.userDao.save(new Bidder(BIDDER_ID, "bidder_e2e",
                BCrypt.hashpw("pwd", BCrypt.gensalt(4)), 100_000_000L));

        Auction auction = new Auction(
                AUCTION_ID, "item-e2e", SELLER_ID,
                5_000_000L, 100_000L,
                AuctionStatus.RUNNING,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusMinutes(30)
        );
        server.auctionDao.save(auction);

        server.start();
    }

    @AfterEach
    void tearDown() { server.stop(); }

    @Test
    @DisplayName("E2E: Login đúng credentials → nhận LoginResult.Success qua socket")
    void e2e_login_success() throws Exception {
        try (TestClient client = TestClient.connect(server.getPort())) {
            client.send(new LoginRequest("bidder_e2e", "pwd"));
            Object response = client.receive(2000);

            assertInstanceOf(LoginResult.Success.class, response);
            User user = ((LoginResult.Success) response).user();
            assertEquals("bidder_e2e", user.getUsername());
        }
    }

    @Test
    @DisplayName("E2E: Login sai password → nhận LoginResult.Failure")
    void e2e_login_wrong_password() throws Exception {
        try (TestClient client = TestClient.connect(server.getPort())) {
            client.send(new LoginRequest("bidder_e2e", "wrong-password"));
            Object response = client.receive(2000);

            assertInstanceOf(LoginResult.Failure.class, response);
        }
    }

    @Test
    @DisplayName("E2E: Login user không tồn tại → nhận LoginResult.Failure")
    void e2e_login_unknown_user() throws Exception {
        try (TestClient client = TestClient.connect(server.getPort())) {
            client.send(new LoginRequest("ghost-user", "any-pwd"));
            Object response = client.receive(2000);

            assertInstanceOf(LoginResult.Failure.class, response);
        }
    }

    @Test
    @DisplayName("E2E: Register user mới → nhận RegisterResult.Success")
    void e2e_register_new_user() throws Exception {
        try (TestClient client = TestClient.connect(server.getPort())) {
            String newUsername = "new_user_" + UUID.randomUUID().toString().substring(0, 8);
            client.send(new RegisterRequest(newUsername, "newpwd", Role.BIDDER));
            Object response = client.receive(2000);

            assertInstanceOf(RegisterResult.Success.class, response);
            assertEquals(newUsername, ((RegisterResult.Success) response).user().getUsername());
            assertTrue(server.userDao.findByUsername(newUsername).isPresent());
        }
    }

    @Test
    @DisplayName("E2E: Register username trùng → nhận RegisterResult.Failure")
    void e2e_register_duplicate_username() throws Exception {
        try (TestClient client = TestClient.connect(server.getPort())) {
            client.send(new RegisterRequest("bidder_e2e", "newpwd", Role.BIDDER));
            Object response = client.receive(2000);

            assertInstanceOf(RegisterResult.Failure.class, response);
        }
    }

    @Test
    @DisplayName("E2E: Place bid hợp lệ → nhận BidResult.Success + auction state cập nhật")
    void e2e_place_bid_success() throws Exception {
        try (TestClient client = TestClient.connect(server.getPort())) {
            client.send(new LoginRequest("bidder_e2e", "pwd"));
            client.receive(2000);

            client.send(new BidRequest(AUCTION_ID, BIDDER_ID, 5_500_000L));
            Object response = client.receive(2000);

            assertInstanceOf(BidResult.Success.class, response);
            Auction returned = ((BidResult.Success) response).auction();
            assertEquals(5_500_000L, returned.getCurrentPrice());

            Auction stored = server.auctionDao.findById(AUCTION_ID).orElseThrow();
            assertEquals(5_500_000L, stored.getCurrentPrice());
            assertEquals(BIDDER_ID, stored.getHighestBidderId());
        }
    }

    @Test
    @DisplayName("E2E: Place bid quá thấp → nhận BidResult.Failure, không thay đổi auction")
    void e2e_place_bid_below_min_increment() throws Exception {
        long priceBefore = server.auctionDao.findById(AUCTION_ID).orElseThrow().getCurrentPrice();

        try (TestClient client = TestClient.connect(server.getPort())) {
            client.send(new LoginRequest("bidder_e2e", "pwd"));
            client.receive(2000);

            client.send(new BidRequest(AUCTION_ID, BIDDER_ID, 5_010_000L));
            Object response = client.receive(2000);

            assertInstanceOf(BidResult.Failure.class, response);
            assertEquals(priceBefore,
                    server.auctionDao.findById(AUCTION_ID).orElseThrow().getCurrentPrice());
        }
    }

    // ════════════════════════════════════════════════════════════
    // TEST CLIENT
    // ════════════════════════════════════════════════════════════

    private static class TestClient implements AutoCloseable {
        final Socket socket;
        final ObjectOutputStream out;
        final ObjectInputStream in;
        final LinkedBlockingQueue<Object> incoming = new LinkedBlockingQueue<>();
        final Thread readerThread;
        volatile boolean closed = false;

        private TestClient(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new ObjectOutputStream(socket.getOutputStream());
            this.out.flush();
            this.in = new ObjectInputStream(socket.getInputStream());
            this.readerThread = new Thread(this::readLoop, "test-client-reader");
            this.readerThread.setDaemon(true);
            this.readerThread.start();
        }

        static TestClient connect(int port) throws IOException {
            return new TestClient(new Socket("localhost", port));
        }

        private void readLoop() {
            try {
                while (!closed && !socket.isClosed()) {
                    Object obj = in.readObject();
                    incoming.offer(obj);
                }
            } catch (Exception ignored) {
            }
        }

        void send(Object msg) throws IOException {
            out.writeObject(msg);
            out.flush();
        }

        Object receive(long timeoutMs) throws InterruptedException {
            return incoming.poll(timeoutMs, TimeUnit.MILLISECONDS);
        }

        @SuppressWarnings("unchecked")
        <T> T receiveAnyEvent(Class<T> type, long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                Object obj = incoming.poll(deadline - System.currentTimeMillis(),
                        TimeUnit.MILLISECONDS);
                if (obj == null) return null;
                if (type.isInstance(obj)) return (T) obj;
            }
            return null;
        }

        @Override
        public void close() {
            closed = true;
            try { socket.close(); } catch (IOException ignore) {}
        }
    }
}
