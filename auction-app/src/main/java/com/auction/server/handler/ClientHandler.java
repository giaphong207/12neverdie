package com.auction.server.handler;

import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.ItemDao;
import com.auction.server.realtime.AuctionSubscriptionManager;
import com.auction.server.realtime.EventBroadcaster;
import com.auction.server.service.AuctionService;
import com.auction.server.service.AuthService;
import com.auction.server.service.BidOutcome;
import com.auction.server.service.BidService;
import com.auction.shared.exception.AppExceptions.*;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.user.User;
import com.auction.shared.networkMessage.AuctionEvents.*;
import com.auction.shared.networkMessage.Requests.*;
import com.auction.shared.networkMessage.Results.*;
import com.auction.shared.factory.ItemFactory;

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
                    handleLoginRequest(req);
                } else if (incoming instanceof RegisterRequest req) {
                    handleRegisterRequest(req);
                } else if (incoming instanceof SubscribeAuctionListRequest) {
                    handleSubscribeAuctionListRequest();
                } else if (incoming instanceof SubscribeAuctionRequest req) {
                    handleSubscribeAuctionRequest(req);
                } else if (incoming instanceof BidRequest req) {
                    handleBidRequest(req);
                } else if (incoming instanceof AddItemRequest req) {
                    handleAddItemRequest(req);
                } else if (incoming instanceof UpdateItemRequest req) {
                    handleUpdateItemRequest(req);
                } else if (incoming instanceof DeleteItemRequest req) {
                    handleDeleteItemRequest(req);
                } else if (incoming instanceof GetSellerItemsRequest req) {
                    handleGetSellerItemsRequest(req);
                }
            }
        } catch (Exception e) {
            System.out.println("Client ngắt kết nối: " + socket.getRemoteSocketAddress());
        } finally {
            cleanUp();
        }
    }

    private void handleLoginRequest(LoginRequest req) {
        try {
            User user = authService.login(req.username(), req.password());
            if (user != null) {
                send(new LoginResult.Success(user));
            } else {
                send(new LoginResult.Failure("Sai tên đăng nhập hoặc mật khẩu"));
            }
        } catch (Exception e) {
            send(new LoginResult.Failure("Lỗi server: " + e.getMessage()));
        }
    }

    private void handleRegisterRequest(RegisterRequest req) {
        try {
            User user = authService.register(req.username(), req.password(), req.role());
            if (user != null) {
                send(new RegisterResult.Success(user));
            } else {
                send(new RegisterResult.Failure("Tên đăng nhập đã tồn tại"));
            }
        } catch (Exception e) {
            send(new RegisterResult.Failure("Lỗi server: " + e.getMessage()));
        }
    }

    private void handleSubscribeAuctionListRequest() {
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

    private void handleSubscribeAuctionRequest(SubscribeAuctionRequest req) {
        subscriptionManager.subscribeAuction(req.auctionId(), this);
        try {
            Optional<Auction> auctionOpt = auctionDao.findById(req.auctionId());
            if (auctionOpt.isPresent()) {
                send(new AuctionUpdatedEvent(auctionOpt.get()));
            }
        } catch (Exception e) {
            System.err.println("Lỗi gửi snapshot auction: " + e.getMessage());
        }
    }

    private void handleBidRequest(BidRequest request) {
        try {
            BidOutcome result = bidService.placeBid(
                    request.auctionId(),
                    request.bidderId(),
                    request.amount());

            // Trả response cho người vừa bid: chỉ cần Auction state mới
            send(new BidResult.Success(result.auction()));

            // Broadcast cho mọi subscriber: kèm thông tin Bid để client biết
            // ai vừa bid bao nhiêu (không chỉ thấy giá đổi)
            broadcaster.broadcast(new BidPlacedEvent(result.auction(), result.bid()));

        } catch (AppException ex) {
            send(new BidResult.Failure(ex.getMessage()));
        } catch (Exception ex) {
            send(new ErrorMessage("Lỗi server khi xử lý bid: " + ex.getMessage()));
            ex.printStackTrace();
        }
    }

    // ===== ITEM MANAGEMENT (MỚI) =====

    private void handleAddItemRequest(AddItemRequest req) {
        try {
            // Validate
            if (req.name() == null || req.name().isBlank()) {
                send(new AddItemResult.Failure("Tên sản phẩm không được rỗng"));
                return;
            }
            if (req.startPrice() <= 0) {
                send(new AddItemResult.Failure("Giá khởi điểm phải > 0"));
                return;
            }
            if (req.type() == null) {
                send(new AddItemResult.Failure("Phải chọn loại sản phẩm"));
                return;
            }

            // Tạo item
            String itemId = UUID.randomUUID().toString();
            Item item = ItemFactory.createItem(
                    req.type(), itemId, req.sellerId(),
                    req.name(), req.description(), req.startPrice());
            itemDao.save(item);

            System.out.println("[Server] Item mới: " + item.getName() + " | seller: " + req.sellerId());

            LocalDateTime now = LocalDateTime.now();
            long minIncrement = Math.max(1000L, req.startPrice() / 100);
            Auction auction = auctionService.createAuction(
                    req.sellerId(), itemId,
                    req.startPrice(), minIncrement,
                    now, now.plusHours(24));

            System.out.println("[Server] Auction mới: " + auction.getId() + " (RUNNING 24h)");

            send(new AddItemResult.Success(item));

        } catch (Exception e) {
            e.printStackTrace();
            send(new AddItemResult.Failure("Lỗi server: " + e.getMessage()));
        }
    }

    private void handleUpdateItemRequest(UpdateItemRequest req) {
        try {
            if (req.name() == null || req.name().isBlank()) {
                send(new UpdateItemResult.Failure("Tên sản phẩm không được rỗng"));
                return;
            }

            Optional<Item> existing = itemDao.findById(req.itemId());
            if (existing.isEmpty()) {
                send(new UpdateItemResult.Failure("Sản phẩm không tồn tại"));
                return;
            }

            Item updated = ItemFactory.createItem(
                    req.type(), req.itemId(), req.sellerId(),
                    req.name(), req.description(), req.startPrice());
            itemDao.save(updated);

            send(new UpdateItemResult.Success(updated));
        } catch (Exception e) {
            e.printStackTrace();
            send(new UpdateItemResult.Failure("Lỗi server: " + e.getMessage()));
        }
    }

    private void handleDeleteItemRequest(DeleteItemRequest req) {
        try {
            Optional<Item> existing = itemDao.findById(req.itemId());
            if (existing.isEmpty()) {
                send(new DeleteItemResult.Failure("Sản phẩm không tồn tại"));
                return;
            }

            itemDao.deleteById(req.itemId());
            send(new DeleteItemResult.Success());
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("foreign key")) {
                send(new DeleteItemResult.Failure("Không xoá được: sản phẩm đang có trong phiên đấu giá"));
            } else {
                send(new DeleteItemResult.Failure("Lỗi server: " + msg));
            }
        }
    }

    private void handleGetSellerItemsRequest(GetSellerItemsRequest req) {
        try {
            List<Item> items = itemDao.findBySellerId(req.sellerId());
            send(new GetSellerItemsResult.Success(items));
        } catch (Exception e) {
            e.printStackTrace();
            send(new GetSellerItemsResult.Failure("Lỗi server: " + e.getMessage()));
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