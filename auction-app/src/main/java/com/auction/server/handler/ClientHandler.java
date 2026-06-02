package com.auction.server.handler;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auction.server.realtime.AuctionEnricher;
import com.auction.server.realtime.AuctionSubscriptionManager;
import com.auction.server.realtime.EventBroadcaster;
import com.auction.server.realtime.EventReceiver;
import com.auction.server.service.AuctionLifecycleService;
import com.auction.server.service.AuctionService;
import com.auction.server.service.AuthService;
import com.auction.server.service.AutoBidService;
import com.auction.server.service.BidOutcome;
import com.auction.server.service.BidService;
import com.auction.server.service.ItemService;
import com.auction.server.service.WalletService;
import com.auction.shared.exception.AppExceptions.AppException;
import com.auction.shared.exception.AppExceptions.AuctionNotFoundException;
import com.auction.shared.exception.AppExceptions.AuthenticationException;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.user.Admin;
import com.auction.shared.model.user.Bidder;
import com.auction.shared.model.user.Role;
import com.auction.shared.model.user.Seller;
import com.auction.shared.model.user.User;
import com.auction.shared.networkMessage.AuctionEvents.*;
import com.auction.shared.networkMessage.Requests.*;
import com.auction.shared.networkMessage.Results.*;
import com.auction.shared.networkMessage.Results.ItemRow;
import com.auction.shared.networkMessage.Results.LoginResult;
import com.auction.shared.networkMessage.Results.RegisterResult;
import com.auction.shared.networkMessage.Results.SetAutoBidResponse;
import com.auction.shared.networkMessage.Results.UpdateItemResult;
import com.auction.shared.networkMessage.Results.UserRow;
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
    private final AuctionLifecycleService lifecycleService;
    private User currentUser; // user đang đăng nhập trên kết nối này (null nếu chưa login)

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
                         AuctionEnricher enricher,
                         AuctionLifecycleService lifecycleService) {
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
        this.lifecycleService = lifecycleService;  
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
                    case GetAllItemsRequest _           -> handleGetAllItemsRequest();
                    case GetAdminStatsRequest _         -> handleGetAdminStatsRequest();
                    case GetBalanceRequest req          -> handleGetBalanceRequest(req);
                    case DepositRequest req             -> handleDepositRequest(req);
                    case SetAutoBidRequest req          -> handleSetAutoBidRequest(req);
                    case DisableAutoBidRequest req      -> handleDisableAutoBidRequest(req);
                    case CancelAuctionRequest req       -> handleCancelAuctionRequest(req);
                    case AdminDeleteItemRequest req     -> handleAdminDeleteItemRequest(req);
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
            this.currentUser = user;
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
            this.currentUser = user;
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
            // Gửi TẤT CẢ phiên (gồm cả đã kết thúc) để client hiển thị đầy đủ;
            // client tự lọc theo trạng thái nếu cần.
            List<Auction> activeAuctions = auctionService.getAllAuctions();
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
            requireLogin();
            if (roleOf(currentUser) != Role.ADMIN) {
                throw new AuthenticationException("Chỉ quản trị viên mới được xem danh sách người dùng");
            }
            List<UserRow> rows = authService.getAllUsers().stream()
                    .map(u -> new UserRow(u.getUsername(), roleOf(u)))
                    .toList();
            send(new GetAllUsersResult.Success(rows));
        } catch (AppException e) {
            send(new GetAllUsersResult.Failure(e.getMessage()));
        } catch (Exception e) {
            log.error("Lỗi lấy danh sách user", e);
            send(new GetAllUsersResult.Failure("Lỗi server: " + e.getMessage()));
        }
    }

    private void handleGetAllItemsRequest() {
        try {
            requireLogin();
            if (roleOf(currentUser) != Role.ADMIN) {
                throw new AuthenticationException("Chỉ quản trị viên mới được xem toàn bộ sản phẩm");
            }

            // Bảng tra id -> username (để hiện tên seller thay vì id)
            java.util.Map<String, String> idToName = new java.util.HashMap<>();
            for (User u : authService.getAllUsers()) {
                idToName.put(u.getId(), u.getUsername());
            }

            List<ItemRow> rows = itemService.getAllItems().stream()
                    .map(it -> new ItemRow(
                            it.getId(),
                            it.getName(),
                            idToName.getOrDefault(it.getSellerId(), it.getSellerId()), // không thấy thì hiện id
                            com.auction.shared.factory.ItemFactory.toItemType(it).name()))
                    .toList();

            send(new GetAllItemsResult.Success(rows));
        } catch (AppException e) {
            send(new GetAllItemsResult.Failure(e.getMessage()));
        } catch (Exception e) {
            log.error("Lỗi lấy danh sách sản phẩm", e);
            send(new GetAllItemsResult.Failure("Lỗi server: " + e.getMessage()));
        }
    }

    private void handleGetAdminStatsRequest() {
        try {
            requireLogin();
            if (roleOf(currentUser) != Role.ADMIN) {
                throw new AuthenticationException("Chỉ quản trị viên mới được xem báo cáo");
            }

            // Người dùng
            List<User> users = authService.getAllUsers();
            long totalUsers = users.size();
            long sellers = users.stream().filter(u -> roleOf(u) == Role.SELLER).count();
            long bidders = users.stream().filter(u -> roleOf(u) == Role.BIDDER).count();

            // Sản phẩm
            long totalItems = itemService.getAllItems().size();

            // Phiên — đếm theo trạng thái + tổng lượt bid + tổng tiền PAID
            List<Auction> auctions = auctionService.getAllAuctions();
            long running = 0, open = 0, finished = 0, paid = 0, canceled = 0;
            long totalBids = 0;
            long totalRevenue = 0;
            for (Auction a : auctions) {
                switch (a.getStatus()) {
                    case RUNNING  -> running++;
                    case OPEN     -> open++;
                    case FINISHED -> finished++;
                    case PAID     -> { paid++; totalRevenue += a.getCurrentPrice(); }
                    case CANCELED -> canceled++;
                }
                if (a.getBidHistory() != null) {
                    totalBids += a.getBidHistory().size();
                }
            }

            AdminStats stats = new AdminStats(
                    totalUsers, sellers, bidders,
                    totalItems,
                    auctions.size(), running, open, finished, paid, canceled,
                    totalBids,
                    totalRevenue);
            send(new GetAdminStatsResult.Success(stats));

        } catch (AppException e) {
            send(new GetAdminStatsResult.Failure(e.getMessage()));
        } catch (Exception e) {
            log.error("Lỗi tạo báo cáo", e);
            send(new GetAdminStatsResult.Failure("Lỗi server: " + e.getMessage()));
        }
    }

    private User requireLogin() {
        if (currentUser == null) {
            throw new AuthenticationException("Bạn cần đăng nhập trước");
        }
        return currentUser;
    }

    // ===== ADMIN/SELLER: HỦY PHIÊN =====
    private void handleCancelAuctionRequest(CancelAuctionRequest req) {
        try {
            requireLogin();
            Auction target = auctionService.getAuctionById(req.auctionId())
                    .orElseThrow(() -> new AuctionNotFoundException(req.auctionId()));
            if (!currentUser.canManage(target)) {
                throw new AuthenticationException("Bạn không có quyền hủy phiên này");
            }
            Auction updated = lifecycleService.cancelAuction(req.auctionId(), roleOf(currentUser));
            log.warn("ADMIN ACTION | {} ({}) HỦY phiên {}",
                    currentUser.getUsername(), roleOf(currentUser), req.auctionId());
            send(new CancelAuctionResult.Success(updated));

        } catch (AppException e) {
            send(new CancelAuctionResult.Failure(e.getMessage()));
        } catch (IllegalStateException e) {            // guard trạng thái từ Auction.cancel()
            send(new CancelAuctionResult.Failure(e.getMessage()));
        } catch (Exception e) {
            log.error("Lỗi hủy phiên", e);
            send(new CancelAuctionResult.Failure("Lỗi server: " + e.getMessage()));
        }
    }

    // ===== ADMIN: GỠ SẢN PHẨM VI PHẠM  =====
    private void handleAdminDeleteItemRequest(AdminDeleteItemRequest req) {
        try {
            requireLogin(); //chỉ admin mới được quyền gỡ, ko lquan đến chủ sở hữu nên ko dùng canManage
            if (roleOf(currentUser) != Role.ADMIN) {
                throw new AuthenticationException("Chỉ quản trị viên mới được gỡ sản phẩm");
            }
            itemService.deleteItemAsAdmin(req.itemId());
            log.warn("ADMIN ACTION | {} GỠ sản phẩm {}", currentUser.getUsername(), req.itemId());
            send(new AdminDeleteItemResult.Success(req.itemId()));

        } catch (AppException e) {
            send(new AdminDeleteItemResult.Failure(e.getMessage()));
        } catch (Exception e) {
            log.error("Lỗi gỡ sản phẩm", e);
            send(new AdminDeleteItemResult.Failure("Lỗi server: " + e.getMessage()));
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
    private void handleDisableAutoBidRequest(DisableAutoBidRequest req) {
        try {
            requireLogin();
            boolean off = autoBidService.disableConfig(req.auctionId(), req.bidderId());
            send(new SetAutoBidResponse(off,
                    off ? "Đã tắt đấu giá tự động"
                            : "Bạn chưa bật đấu giá tự động cho phiên này"));
        } catch (AppException e) {
            send(new SetAutoBidResponse(false, e.getMessage()));
        } catch (Exception e) {
            log.error("Lỗi tắt auto-bid", e);
            send(new SetAutoBidResponse(false, "Lỗi server: " + e.getMessage()));
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