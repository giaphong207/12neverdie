package com.auction.server.handler;

import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.ItemDao;
import com.auction.server.realtime.AuctionSubscriptionManager;
import com.auction.server.realtime.EventBroadcaster;
import com.auction.server.service.AuctionService;
import com.auction.server.service.AuthService;
import com.auction.server.service.BidOutcome;
import com.auction.server.service.BidService;
import com.auction.server.service.WalletService;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final BidService bidService;
    private final AuthService authService;
    private final WalletService walletService;
    private final AuctionDao auctionDao;
    private final AuctionService auctionService;
    private final ItemDao itemDao;
    private final AuctionSubscriptionManager subscriptionManager;
    private final EventBroadcaster broadcaster;

    private ObjectOutputStream out;
    private ObjectInputStream in;

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);
    public ClientHandler(Socket socket,
                         BidService bidService,
                         AuthService authService,
                         WalletService walletService,
                         AuctionDao auctionDao,
                         AuctionService auctionService,
                         ItemDao itemDao,
                         AuctionSubscriptionManager subscriptionManager,
                         EventBroadcaster broadcaster) {
        this.socket = socket;
        this.bidService = bidService;
        this.authService = authService;
        this.walletService = walletService;
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
                } else if (incoming instanceof GetBalanceRequest req) {
                    handleGetBalanceRequest(req);
                } else if (incoming instanceof DepositRequest req) {
                    handleDepositRequest(req);
                }
            }
        } catch (Exception e) {
            log.info("Client ngắt kết nối: {}", socket.getRemoteSocketAddress());
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
            log.debug("Gửi {} auction snapshot cho client {}",
                    activeAuctions.size(), socket.getRemoteSocketAddress());
            for (Auction a : activeAuctions) {
                send(new AuctionUpdatedEvent(a));
            }
        } catch (Exception e) {
            log.error("Lỗi gửi snapshot danh sách", e);
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
            log.error("Lỗi gửi snapshot auction", e);
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
        }catch (Exception ex) {
        send(new ErrorMessage("Lỗi server khi xử lý bid: " + ex.getMessage()));
        log.error("Lỗi server khi xử lý bid", ex);
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

            LocalDateTime now = LocalDateTime.now();
            if (req.startTime() == null || req.endTime() == null) {
                send(new AddItemResult.Failure("Phải chọn thời gian bắt đầu và kết thúc"));
                return;
            }
            if (!req.endTime().isAfter(req.startTime())) {
                send(new AddItemResult.Failure("Thời gian kết thúc phải sau thời gian bắt đầu"));
                return;
            }
            if (req.startTime().isBefore(now.minusMinutes(1))) {
                send(new AddItemResult.Failure("Thời gian bắt đầu không được trong quá khứ"));
                return;
            }

            // Tạo item
            String itemId = UUID.randomUUID().toString();
            Item item = ItemFactory.createItem(
                    req.type(), itemId, req.sellerId(),
                    req.name(), req.description(), req.startPrice());
            itemDao.save(item);

            long minIncrement = Math.max(1000L, req.startPrice() / 100);
            Auction auction = auctionService.createAuction(
                    req.sellerId(), itemId,
                    req.startPrice(), minIncrement,
                    req.startTime(), req.endTime());

            log.info("Item mới: {} | seller: {} | start: {} | end: {}",
                    item.getName(), req.sellerId(), auction.getStartTime(), auction.getEndTime());

            send(new AddItemResult.Success(item));

        } catch (Exception e) {
            log.error("Lỗi tạo item/auction", e);
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
            log.error("Lỗi cập nhật item", e);
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
            log.error("Lỗi cập nhật item", e);
            send(new GetSellerItemsResult.Failure("Lỗi server: " + e.getMessage()));
        }
    }

    // ===== WALLET =====

    private void handleGetBalanceRequest(GetBalanceRequest req) {
        try {
            long balance = walletService.getBalance(req.userId());
            send(new GetBalanceResult.Success(balance));
        } catch (Exception e) {
            log.error("Lỗi getBalance", e);
            send(new GetBalanceResult.Failure(e.getMessage()));
        }
    }

    private void handleDepositRequest(DepositRequest req) {
        try {
            long newBalance = walletService.deposit(req.userId(), req.amount());
            send(new DepositResult.Success(newBalance));
        } catch (Exception e) {
            log.error("Lỗi deposit", e);
            send(new DepositResult.Failure(e.getMessage()));
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