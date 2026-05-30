package com.auction.server.handler;

import com.auction.server.realtime.AuctionEnricher;
import com.auction.server.realtime.AuctionSubscriptionManager;
import com.auction.server.realtime.EventBroadcaster;
import com.auction.server.realtime.EventReceiver;
import com.auction.server.service.*;
import com.auction.shared.exception.AppExceptions.*;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.user.*;
import com.auction.shared.networkMessage.AuctionEvents.*;
import com.auction.shared.networkMessage.Requests.*;
import com.auction.shared.networkMessage.Results.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class ClientHandler implements Runnable, EventReceiver {
    private final Socket socket;
    private final BidService bidService;
    private final AuthService authService;
    private final WalletService walletService;
    private final AuctionService auctionService;
    private final ItemService itemService;
    private final AutoBidService autoBidService;
    private final AuctionSubscriptionManager subscriptionManager;
    private final EventBroadcaster broadcaster;
    private final AuctionEnricher enricher;

    private ObjectOutputStream out;
    private ObjectInputStream in;

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);
    public ClientHandler(Socket socket,
                         BidService bidService,
                         AuthService authService,
                         WalletService walletService,
                         AuctionService auctionService,
                         ItemService itemService,
                         AutoBidService autoBidService,
                         AuctionSubscriptionManager subscriptionManager,
                         EventBroadcaster broadcaster,
                         AuctionEnricher enricher) {
        this.socket = socket;
        this.bidService = bidService;
        this.authService = authService;
        this.walletService = walletService;
        this.auctionService = auctionService;
        this.itemService = itemService;
        this.autoBidService = autoBidService;
        this.subscriptionManager = subscriptionManager;
        this.broadcaster = broadcaster;
        this.enricher = enricher;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            while (!socket.isClosed()) {
                Object incoming = in.readObject();

                switch (incoming) {
                    case LoginRequest req               -> handleLoginRequest(req);
                    case RegisterRequest req            -> handleRegisterRequest(req);
                    case SubscribeAuctionListRequest _  -> handleSubscribeAuctionListRequest();
                    case SubscribeAuctionRequest req    -> handleSubscribeAuctionRequest(req);
                    case BidRequest req                 -> handleBidRequest(req);
                    case AddItemRequest req             -> handleAddItemRequest(req);
                    case UpdateItemRequest req          -> handleUpdateItemRequest(req);
                    case DeleteItemRequest req          -> handleDeleteItemRequest(req);
                    case GetSellerItemsRequest req      -> handleGetSellerItemsRequest(req);
                    case GetAllUsersRequest _            -> handleGetAllUsersRequest();
                    case GetBalanceRequest req          -> handleGetBalanceRequest(req);
                    case DepositRequest req             -> handleDepositRequest(req);
                    case SetAutoBidRequest req          -> handleSetAutoBidRequest(req);
                    case null    -> log.warn("Nhận message null từ client");
                    default      -> log.warn("Nhận message không xác định: {}",
                            incoming.getClass().getSimpleName());
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
            send(new LoginResult.Success(user));
        } catch (AppException e) {
            send(new LoginResult.Failure(e.getMessage()));
        } catch (Exception e) {
            log.error("Lỗi server khi login", e);
            send(new LoginResult.Failure("Lỗi server: " + e.getMessage()));
        }
    }

    private void handleRegisterRequest(RegisterRequest req) {
        try {
            User user = authService.register(req.username(), req.password(), req.role());
            send(new RegisterResult.Success(user));
        } catch (AppException e) {
            send(new RegisterResult.Failure(e.getMessage()));
        } catch (Exception e) {
            log.error("Lỗi server khi register", e);
            send(new RegisterResult.Failure("Lỗi server: " + e.getMessage()));
        }
    }

    private void handleSubscribeAuctionListRequest() {
        subscriptionManager.subscribeList(this);
        try {
            List<Auction> activeAuctions = auctionService.getActiveAuctions();
            log.debug("Gửi {} auction snapshot cho client {}",
                    activeAuctions.size(), socket.getRemoteSocketAddress());
            for (Auction a : activeAuctions) {
                enricher.enrich(a);
                send(new AuctionUpdatedEvent(a));
            }
        } catch (Exception e) {
            log.error("Lỗi gửi snapshot danh sách", e);
        }
    }

    private void handleSubscribeAuctionRequest(SubscribeAuctionRequest req) {
        subscriptionManager.subscribeAuction(req.auctionId(), this);
        try {
            Optional<Auction> auctionOpt = auctionService.getAuctionById(req.auctionId());
            if (auctionOpt.isPresent()) {
                Auction a = auctionOpt.get();
                enricher.enrich(a);
                send(new AuctionUpdatedEvent(a));
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

            if (result.extendedSeconds() > 0) {
                broadcaster.broadcast(new AuctionExtendedEvent(result.auction(), result.extendedSeconds()));
            }

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
            // Check quá khứ — record không lo, vì phụ thuộc "now" của server
            if (req.startTime().isBefore(LocalDateTime.now().minusMinutes(1))) {
                send(new AddItemResult.Failure("Thời gian bắt đầu không được trong quá khứ"));
                return;
            }

            // Service tạo item (tự validate name/price/type + tạo + save)
            Item item = itemService.addItem(
                    req.sellerId(), req.name(), req.description(),
                    req.startPrice(), req.type());

            // Auction theo giờ seller chọn (null + endTime>startTime đã được record check)
            long minIncrement = Math.max(1000L, req.startPrice() / 100);
            Auction auction = auctionService.createAuction(
                    req.sellerId(), item.getId(),
                    req.startPrice(), minIncrement,
                    req.startTime(), req.endTime());

            log.info("Item mới: {} | seller: {} | start: {} | end: {}",
                    item.getName(), req.sellerId(), auction.getStartTime(), auction.getEndTime());

            send(new AddItemResult.Success(item));

        } catch (AppException e) {
            send(new AddItemResult.Failure(e.getMessage()));
        } catch (Exception e) {
            log.error("Lỗi tạo item/auction", e);
            send(new AddItemResult.Failure("Lỗi server: " + e.getMessage()));
        }
    }

    private void handleUpdateItemRequest(UpdateItemRequest req) {
        try {
            Item updated = itemService.updateItem(
                    req.itemId(), req.sellerId(),
                    req.name(), req.description(),
                    req.startPrice(), req.type());

            send(new UpdateItemResult.Success(updated));

        } catch (AppException e) {
            send(new UpdateItemResult.Failure(e.getMessage()));
        } catch (Exception e) {
            log.error("Lỗi cập nhật item", e);
            send(new UpdateItemResult.Failure("Lỗi server: " + e.getMessage()));
        }
    }

    private void handleDeleteItemRequest(DeleteItemRequest req) {
        try {
            itemService.deleteItem(req.itemId(), req.sellerId());
            send(new DeleteItemResult.Success());

        } catch (AppException e) {
            send(new DeleteItemResult.Failure(e.getMessage()));
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("foreign key")) {
                send(new DeleteItemResult.Failure("Không xoá được: sản phẩm đang có trong phiên đấu giá"));
            } else {
                log.error("Lỗi xóa item", e);
                send(new DeleteItemResult.Failure("Lỗi server: " + msg));
            }
        }
    }

    private void handleGetSellerItemsRequest(GetSellerItemsRequest req) {
        try {
            List<Item> items = itemService.getItemsBySeller(req.sellerId());
            send(new GetSellerItemsResult.Success(items));
        } catch (Exception e) {
            log.error("Lỗi cập nhật item", e);
            send(new GetSellerItemsResult.Failure("Lỗi server: " + e.getMessage()));
        }
    }
    private void handleGetAllUsersRequest() {
        try {
            List<UserRow> rows = authService.getAllUsers().stream()
                    .map(u -> new UserRow(u.getUsername(), roleOf(u)))
                    .toList();
            send(new GetAllUsersResult.Success(rows));
        } catch (Exception e) {
            log.error("Lỗi lấy danh sách user", e);
            send(new GetAllUsersResult.Failure("Lỗi server: " + e.getMessage()));
        }
    }

    private static Role roleOf(User u) {
        return switch (u) {
            case Admin a  -> Role.ADMIN;
            case Seller s -> Role.SELLER;
            case Bidder b -> Role.BIDDER;
        };
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

    private void handleSetAutoBidRequest(SetAutoBidRequest req) {
        try {
            // Validate cơ bản (phòng khi client gửi số xấu)
            if (req.maxAmount() <= 0 || req.increment() <= 0) {
                send(new SetAutoBidResponse(false, "Mức tối đa và bước giá phải lớn hơn 0"));
                return;
            }

            // Fix #3 — check ví: không cho auto-bid vượt số dư hiện có.
            //   (Check tại thời điểm set; nếu sau này ví tụt thì không đảm bảo tuyệt đối,
            //    nhưng đủ để không tự bid hộ user quá số tiền họ từng có.)
            long balance = walletService.getBalance(req.bidderId());
            if (req.maxAmount() > balance) {
                send(new SetAutoBidResponse(false,
                        "Mức tối đa vượt số dư ví (ví hiện có " + balance + " VNĐ)"));
                return;
            }

            autoBidService.upsertConfig(
                    req.auctionId(), req.bidderId(),
                    req.maxAmount(), req.increment());

            send(new SetAutoBidResponse(true, "Đã thiết lập đấu giá tự động"));

        } catch (Exception e) {
            log.error("Lỗi setAutoBid", e);
            send(new SetAutoBidResponse(false, "Không thể thiết lập: " + e.getMessage()));
        }
    }
    @Override
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