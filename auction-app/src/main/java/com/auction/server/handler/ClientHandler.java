package com.auction.server.handler;

import com.auction.server.DAO.AuctionDao;
import com.auction.server.DAO.ItemDao;
import com.auction.server.realtime.AuctionSubscriptionManager;
import com.auction.server.realtime.EventBroadcaster;
import com.auction.server.service.AuctionService;
import com.auction.server.service.AuthService;
import com.auction.server.service.BidResult;
import com.auction.server.service.BidService;
import com.auction.shared.exception.AppExceptions.*;
import com.auction.shared.model.Auction;
import com.auction.shared.model.Item;
import com.auction.shared.model.User;
import com.auction.shared.networkMessage.event.AuctionEvents.*;
import com.auction.shared.networkMessage.request.*;
import com.auction.shared.networkMessage.response.*;
import com.auction.shared.pattern.ItemFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final BidService bidService;
    private final AuthService authService;
    private final AuctionDao auctionDao;
    private final AuctionService auctionService;
    private final ItemDao itemDao;
    private final AuctionSubscriptionManager subscriptionManager;
    private final EventBroadcaster broadcaster;

    private ObjectOutputStream out;
    private ObjectInputStream in;

    public ClientHandler(Socket socket,
                         BidService bidService,
                         AuthService authService,
                         AuctionDao auctionDao,
                         AuctionService auctionService,
                         ItemDao itemDao,
                         AuctionSubscriptionManager subscriptionManager,
                         EventBroadcaster broadcaster) {
        this.socket = socket;
        this.bidService = bidService;
        this.authService = authService;
        this.auctionDao = auctionDao;
        this.auctionService = auctionService;
        this.itemDao = itemDao;
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
                } else if (incoming instanceof AddItemRequest req) {
                    handleAddItem(req);
                } else if (incoming instanceof UpdateItemRequest req) {
                    handleUpdateItem(req);
                } else if (incoming instanceof DeleteItemRequest req) {
                    handleDeleteItem(req);
                } else if (incoming instanceof GetSellerItemsRequest req) {
                    handleGetSellerItems(req);
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

    private void handleSubscribeList() {
        subscriptionManager.subscribeList(this);
        try {
            List<Auction> activeAuctions = auctionDao.findActiveAuctions();
            System.out.println("[Server] Gửi " + activeAuctions.size()
                    + " auction snapshot cho client " + socket.getRemoteSocketAddress());
            for (Auction a : activeAuctions) {
                send(new AuctionUpdatedEvent(a));
            }
        } catch (Exception e) {
            System.err.println("Lỗi gửi snapshot danh sách: " + e.getMessage());
        }
    }

    private void handleSubscribeAuction(SubscribeAuctionRequest req) {
        subscriptionManager.subscribeAuction(req.getAuctionId(), this);
        try {
            Optional<Auction> auctionOpt = auctionDao.findById(req.getAuctionId());
            if (auctionOpt.isPresent()) {
                send(new AuctionUpdatedEvent(auctionOpt.get()));
            }
        } catch (Exception e) {
            System.err.println("Lỗi gửi snapshot auction: " + e.getMessage());
        }
    }

    private void handleBidRequest(BidRequest request) {
        try {
            BidResult result = bidService.placeBid(
                    request.getAuctionId(),
                    request.getBidderId(),
                    request.getAmount());

            // Trả response cho người vừa bid: chỉ cần Auction state mới
            send(new BidResponse(true, "Đặt giá thành công!", result.auction()));

            // Broadcast cho mọi subscriber: kèm thông tin Bid để client biết
            // ai vừa bid bao nhiêu (không chỉ thấy giá đổi)
            broadcaster.broadcast(new BidPlacedEvent(result.auction(), result.bid()));

        } catch (AppException ex) {
            send(new BidResponse(false, ex.getMessage(), null));
        } catch (Exception ex) {
            send(new ErrorMessage("Lỗi server khi xử lý bid: " + ex.getMessage()));
            ex.printStackTrace();
        }
    }

    // ===== ITEM MANAGEMENT (MỚI) =====

    private void handleAddItem(AddItemRequest req) {
        try {
            // Validate
            if (req.getName() == null || req.getName().isBlank()) {
                send(new AddItemResponse(false, "Tên sản phẩm không được rỗng", null));
                return;
            }
            if (req.getStartPrice() <= 0) {
                send(new AddItemResponse(false, "Giá khởi điểm phải > 0", null));
                return;
            }
            if (req.getType() == null) {
                send(new AddItemResponse(false, "Phải chọn loại sản phẩm", null));
                return;
            }

            // Tạo item
            String itemId = UUID.randomUUID().toString();
            Item item = ItemFactory.createItem(
                    req.getType(), itemId, req.getSellerId(),
                    req.getName(), req.getDescription(), req.getStartPrice());
            itemDao.save(item);

            System.out.println("[Server] Item mới: " + item.getName() + " | seller: " + req.getSellerId());


            LocalDateTime now = LocalDateTime.now();
            long minIncrement = Math.max(1000L, req.getStartPrice() / 100); // 1% giá khởi điểm
            // Tạo Auction qua Service
            Auction auction = auctionService.createAuction(
                    req.getSellerId(), itemId,
                    req.getStartPrice(), minIncrement,
                    now, now.plusHours(24));

            System.out.println("[Server] Auction mới: " + auction.getId() + " (RUNNING 24h)");

            send(new AddItemResponse(true, "Đã thêm sản phẩm và tạo phiên đấu giá 24h", item));

        } catch (Exception e) {
            e.printStackTrace();
            send(new AddItemResponse(false, "Lỗi server: " + e.getMessage(), null));
        }
    }

    private void handleUpdateItem(UpdateItemRequest req) {
        try {
            if (req.getName() == null || req.getName().isBlank()) {
                send(new UpdateItemResponse(false, "Tên sản phẩm không được rỗng", null));
                return;
            }

            Optional<Item> existing = itemDao.findById(req.getItemId());
            if (existing.isEmpty()) {
                send(new UpdateItemResponse(false, "Sản phẩm không tồn tại", null));
                return;
            }

            // Tạo item mới với cùng id → save() sẽ UPSERT
            Item updated = ItemFactory.createItem(
                    req.getType(), req.getItemId(), req.getSellerId(),
                    req.getName(), req.getDescription(), req.getStartPrice());
            itemDao.save(updated);

            send(new UpdateItemResponse(true, "Đã cập nhật sản phẩm", updated));
        } catch (Exception e) {
            e.printStackTrace();
            send(new UpdateItemResponse(false, "Lỗi server: " + e.getMessage(), null));
        }
    }

    private void handleDeleteItem(DeleteItemRequest req) {
        try {
            Optional<Item> existing = itemDao.findById(req.getItemId());
            if (existing.isEmpty()) {
                send(new DeleteItemResponse(false, "Sản phẩm không tồn tại"));
                return;
            }

            itemDao.deleteById(req.getItemId());
            send(new DeleteItemResponse(true, "Đã xoá sản phẩm"));
        } catch (Exception e) {
            // Có thể fail nếu item đang được dùng trong auction (FK RESTRICT)
            String msg = e.getMessage();
            if (msg != null && msg.contains("foreign key")) {
                send(new DeleteItemResponse(false, "Không xoá được: sản phẩm đang có trong phiên đấu giá"));
            } else {
                send(new DeleteItemResponse(false, "Lỗi server: " + msg));
            }
        }
    }

    private void handleGetSellerItems(GetSellerItemsRequest req) {
        try {
            List<Item> items = itemDao.findBySellerId(req.getSellerId());
            send(new GetSellerItemsResponse(true, "OK", items));
        } catch (Exception e) {
            e.printStackTrace();
            send(new GetSellerItemsResponse(false, "Lỗi server: " + e.getMessage(), null));
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