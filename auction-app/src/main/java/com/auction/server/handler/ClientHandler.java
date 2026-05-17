package com.auction.server.handler;

import com.auction.server.dao.AuctionDao;
import com.auction.server.realtime.AuctionSubscriptionManager;
import com.auction.server.realtime.EventBroadcaster;
import com.auction.server.service.AuthService;
import com.auction.server.service.BidService;
import com.auction.shared.exception.AppException;
import com.auction.shared.model.Auction;
import com.auction.shared.model.User;
import com.auction.shared.network.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Optional;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final BidService bidService;
    private final AuthService authService;
    private final AuctionDao auctionDao;
    private final AuctionSubscriptionManager subscriptionManager;
    private final EventBroadcaster broadcaster;

    private ObjectOutputStream out;
    private ObjectInputStream in;

    public ClientHandler(Socket socket,
                         BidService bidService,
                         AuthService authService,
                         AuctionDao auctionDao,
                         AuctionSubscriptionManager subscriptionManager,
                         EventBroadcaster broadcaster) {
        this.socket = socket;
        this.bidService = bidService;
        this.authService = authService;
        this.auctionDao = auctionDao;
        this.subscriptionManager = subscriptionManager;
        this.broadcaster = broadcaster;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            while (!socket.isClosed()) {
                Object incoming = in.readObject();

                if (incoming instanceof LoginRequest req) {
                    handleLogin(req);
                } else if (incoming instanceof RegisterRequest req) {
                    handleRegister(req);
                } else if (incoming instanceof SubscribeAuctionListRequest) {
                    handleSubscribeList();
                } else if (incoming instanceof SubscribeAuctionRequest req) {
                    handleSubscribeAuction(req);
                } else if (incoming instanceof BidRequest req) {
                    handleBidRequest(req);
                }
            }
        } catch (Exception e) {
            System.out.println("Client ngắt kết nối: " + socket.getRemoteSocketAddress());
        } finally {
            cleanUp();
        }
    }

    private void handleLogin(LoginRequest req) {
        try {
            User user = authService.login(req.getUsername(), req.getPassword());
            if (user != null) {
                send(new LoginResponse(true, "Đăng nhập thành công", user));
            } else {
                send(new LoginResponse(false, "Sai tên đăng nhập hoặc mật khẩu", null));
            }
        } catch (Exception e) {
            send(new LoginResponse(false, "Lỗi server: " + e.getMessage(), null));
        }
    }

    private void handleRegister(RegisterRequest req) {
        try {
            User user = authService.register(req.getUsername(), req.getPassword(), req.getRole());
            if (user != null) {
                send(new RegisterResponse(true, "Đăng ký thành công", user));
            } else {
                send(new RegisterResponse(false, "Tên đăng nhập đã tồn tại", null));
            }
        } catch (Exception e) {
            send(new RegisterResponse(false, "Lỗi server: " + e.getMessage(), null));
        }
    }

    /**
     * Khi client subscribe danh sách:
     *  1. Thêm client vào subscription manager
     *  2. GỬI NGAY danh sách auction hiện có cho client (snapshot)
     */
    private void handleSubscribeList() {
        subscriptionManager.subscribeList(this);
        try {
            List<Auction> activeAuctions = auctionDao.findActiveAuctions();
            System.out.println("[Server] Gửi " + activeAuctions.size()
                    + " auction snapshot cho client " + socket.getRemoteSocketAddress());
            // Gửi từng auction qua AuctionUpdateEvent
            for (Auction a : activeAuctions) {
                send(new AuctionUpdateEvent(a));
            }
        } catch (Exception e) {
            System.err.println("Lỗi gửi snapshot danh sách: " + e.getMessage());
        }
    }

    /**
     * Khi client subscribe 1 auction cụ thể (mở AuctionDetail):
     *  1. Thêm vào subscription
     *  2. Gửi snapshot auction đó
     */
    private void handleSubscribeAuction(SubscribeAuctionRequest req) {
        subscriptionManager.subscribeAuction(req.getAuctionId(), this);
        try {
            Optional<Auction> auctionOpt = auctionDao.findById(req.getAuctionId());
            if (auctionOpt.isPresent()) {
                send(new AuctionUpdateEvent(auctionOpt.get()));
            }
        } catch (Exception e) {
            System.err.println("Lỗi gửi snapshot auction: " + e.getMessage());
        }
    }

    private void handleBidRequest(BidRequest request) {
        try {
            Auction updated = bidService.placeBid(
                    request.getAuctionId(),
                    request.getBidderId(),
                    request.getAmount());
            send(new BidResponse(true, "Đặt giá thành công!", updated));
            broadcaster.broadcastAuctionUpdate(updated);
        } catch (AppException ex) {
            send(new BidResponse(false, ex.getMessage(), null));
        } catch (Exception ex) {
            send(new ErrorMessage("Lỗi server khi xử lý bid: " + ex.getMessage()));
            ex.printStackTrace();
        }
    }

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