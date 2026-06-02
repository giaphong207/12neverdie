package com.auction.server.service;

import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.ItemDao;
import com.auction.shared.exception.AppExceptions.InvalidItemException;
import com.auction.shared.exception.AppExceptions.ItemNotFoundException;
import com.auction.shared.factory.ItemFactory;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;
import com.auction.shared.model.item.ArtItem;
import com.auction.shared.model.item.ElectronicsItem;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.item.ItemType;
import com.auction.shared.model.item.VehicleItem;
import com.auction.shared.model.user.Role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ItemService - CRUD sản phẩm + admin delete cascade")
class ItemServiceTest {

    private FakeItemDao itemDao;
    private FakeAuctionDao auctionDao;
    private FakeLifecycleService lifecycleService;
    private DefaultItemService service;

    private static final String SELLER_A = "seller-A";
    private static final String SELLER_B = "seller-B";

    @BeforeEach
    void setUp() {
        itemDao = new FakeItemDao();
        auctionDao = new FakeAuctionDao();
        lifecycleService = new FakeLifecycleService(auctionDao);
        service = new DefaultItemService(itemDao, auctionDao, lifecycleService);
    }

    // ════════════════════════════════════════════════════════════
    // 1. CONSTRUCTOR validation
    // ════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("ItemDao null → InvalidItemException")
        void null_item_dao_throws() {
            assertThrows(InvalidItemException.class,
                    () -> new DefaultItemService(null, auctionDao, lifecycleService));
        }

        @Test
        @DisplayName("AuctionDao null → InvalidItemException")
        void null_auction_dao_throws() {
            assertThrows(InvalidItemException.class,
                    () -> new DefaultItemService(itemDao, null, lifecycleService));
        }

        @Test
        @DisplayName("LifecycleService null → InvalidItemException")
        void null_lifecycle_throws() {
            assertThrows(InvalidItemException.class,
                    () -> new DefaultItemService(itemDao, auctionDao, null));
        }
    }

    // ════════════════════════════════════════════════════════════
    // 2. addItem
    // ════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("addItem")
    class AddItem {

        @Test
        @DisplayName("Happy path: tạo Electronics item, lưu DAO, trả về item")
        void add_electronics_item_happy_path() {
            Item created = service.addItem(SELLER_A, "iPhone", "Điện thoại", 5_000_000L, ItemType.ELECTRONICS);

            assertNotNull(created);
            assertNotNull(created.getId());
            assertEquals(SELLER_A, created.getSellerId());
            assertEquals("iPhone", created.getName());
            assertEquals(5_000_000L, created.getStartPrice());
            assertInstanceOf(ElectronicsItem.class, created);

            // Verify lưu vào DAO
            assertTrue(itemDao.findById(created.getId()).isPresent());
        }

        @Test
        @DisplayName("Tạo ArtItem dùng ItemType.ART")
        void add_art_item() {
            Item created = service.addItem(SELLER_A, "Tranh sơn dầu", "Họa sĩ X", 10_000_000L, ItemType.ART);
            assertInstanceOf(ArtItem.class, created);
        }

        @Test
        @DisplayName("Tạo VehicleItem dùng ItemType.VEHICLE")
        void add_vehicle_item() {
            Item created = service.addItem(SELLER_A, "Honda Wave", "Xe máy cũ", 8_000_000L, ItemType.VEHICLE);
            assertInstanceOf(VehicleItem.class, created);
        }

        @Test
        @DisplayName("sellerId rỗng/null → InvalidItemException")
        void blank_seller_throws() {
            assertThrows(InvalidItemException.class,
                    () -> service.addItem(null, "X", "desc", 100L, ItemType.ART));
            assertThrows(InvalidItemException.class,
                    () -> service.addItem("", "X", "desc", 100L, ItemType.ART));
            assertThrows(InvalidItemException.class,
                    () -> service.addItem("  ", "X", "desc", 100L, ItemType.ART));
        }

        @Test
        @DisplayName("Name rỗng → InvalidItemException")
        void blank_name_throws() {
            assertThrows(InvalidItemException.class,
                    () -> service.addItem(SELLER_A, "", "desc", 100L, ItemType.ART));
            assertThrows(InvalidItemException.class,
                    () -> service.addItem(SELLER_A, null, "desc", 100L, ItemType.ART));
        }

        @Test
        @DisplayName("Description rỗng → InvalidItemException")
        void blank_description_throws() {
            assertThrows(InvalidItemException.class,
                    () -> service.addItem(SELLER_A, "Item", "", 100L, ItemType.ART));
            assertThrows(InvalidItemException.class,
                    () -> service.addItem(SELLER_A, "Item", "   ", 100L, ItemType.ART));
        }

        @Test
        @DisplayName("startPrice = 0 → InvalidItemException")
        void zero_start_price_throws() {
            assertThrows(InvalidItemException.class,
                    () -> service.addItem(SELLER_A, "Item", "desc", 0L, ItemType.ART));
        }

        @Test
        @DisplayName("startPrice âm → InvalidItemException")
        void negative_start_price_throws() {
            assertThrows(InvalidItemException.class,
                    () -> service.addItem(SELLER_A, "Item", "desc", -100L, ItemType.ART));
        }

        @Test
        @DisplayName("ItemType null → InvalidItemException")
        void null_type_throws() {
            assertThrows(InvalidItemException.class,
                    () -> service.addItem(SELLER_A, "Item", "desc", 100L, null));
        }

        @Test
        @DisplayName("Mỗi addItem sinh UUID khác nhau")
        void each_item_has_unique_id() {
            Item a = service.addItem(SELLER_A, "A", "d", 100L, ItemType.ART);
            Item b = service.addItem(SELLER_A, "A", "d", 100L, ItemType.ART);
            assertNotEquals(a.getId(), b.getId());
        }
    }

    // ════════════════════════════════════════════════════════════
    // 3. updateItem (authorization + validation)
    // ════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("updateItem")
    class UpdateItem {

        @Test
        @DisplayName("Happy path: seller sửa item của mình → tên/desc/giá/type được cập nhật")
        void update_own_item_happy_path() {
            Item original = service.addItem(SELLER_A, "Old name", "old desc", 100L, ItemType.ART);

            Item updated = service.updateItem(
                    original.getId(), SELLER_A,
                    "New name", "new desc", 999L, ItemType.ART);

            assertEquals(original.getId(), updated.getId(), "ID giữ nguyên (UPSERT)");
            assertEquals("New name", updated.getName());
            assertEquals(999L, updated.getStartPrice());

            // Verify trong DAO cũng cập nhật
            Item stored = itemDao.findById(original.getId()).orElseThrow();
            assertEquals("New name", stored.getName());
        }

        @Test
        @DisplayName("Đổi loại item: ART → ELECTRONICS (re-create subclass đúng)")
        void update_changes_item_type() {
            Item original = service.addItem(SELLER_A, "X", "d", 100L, ItemType.ART);
            assertInstanceOf(ArtItem.class, original);

            Item updated = service.updateItem(
                    original.getId(), SELLER_A,
                    "X", "d", 100L, ItemType.ELECTRONICS);

            assertInstanceOf(ElectronicsItem.class, updated,
                    "Sau update sang ELECTRONICS phải là ElectronicsItem");
        }

        @Test
        @DisplayName("Item không tồn tại → ItemNotFoundException")
        void update_non_existent_item_throws() {
            assertThrows(ItemNotFoundException.class,
                    () -> service.updateItem("ghost-id", SELLER_A,
                            "X", "d", 100L, ItemType.ART));
        }

        @Test
        @DisplayName("Authorization: seller B không sửa được item của seller A → InvalidItemException")
        void update_other_sellers_item_throws() {
            Item ownedByA = service.addItem(SELLER_A, "Old", "d", 100L, ItemType.ART);

            InvalidItemException ex = assertThrows(InvalidItemException.class,
                    () -> service.updateItem(ownedByA.getId(), SELLER_B,
                            "Hacked", "d", 100L, ItemType.ART));
            assertTrue(ex.getMessage().contains("không có quyền"),
                    "Phải báo lỗi quyền");

            // Verify state KHÔNG bị thay đổi
            Item stored = itemDao.findById(ownedByA.getId()).orElseThrow();
            assertEquals("Old", stored.getName());
        }

        @Test
        @DisplayName("itemId rỗng → InvalidItemException")
        void blank_item_id_throws() {
            assertThrows(InvalidItemException.class,
                    () -> service.updateItem("", SELLER_A, "X", "d", 100L, ItemType.ART));
            assertThrows(InvalidItemException.class,
                    () -> service.updateItem(null, SELLER_A, "X", "d", 100L, ItemType.ART));
        }

        @Test
        @DisplayName("update với startPrice ≤ 0 → InvalidItemException")
        void update_with_invalid_price_throws() {
            Item original = service.addItem(SELLER_A, "X", "d", 100L, ItemType.ART);
            assertThrows(InvalidItemException.class,
                    () -> service.updateItem(original.getId(), SELLER_A,
                            "X", "d", 0L, ItemType.ART));
            assertThrows(InvalidItemException.class,
                    () -> service.updateItem(original.getId(), SELLER_A,
                            "X", "d", -1L, ItemType.ART));
        }
    }

    // ════════════════════════════════════════════════════════════
    // 4. deleteItemAsAdmin (cascade + history preservation)
    // ════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("deleteItemAsAdmin")
    class DeleteItemAsAdmin {

        @Test
        @DisplayName("Happy path: admin xóa item không có phiên đấu giá nào")
        void delete_item_with_no_auctions() {
            Item item = service.addItem(SELLER_A, "X", "d", 100L, ItemType.ART);

            service.deleteItemAsAdmin(item.getId());

            assertTrue(itemDao.findById(item.getId()).isEmpty());
            assertEquals(0, lifecycleService.cancelCount(),
                    "Không có phiên nào → không cancel gì");
        }

        @Test
        @DisplayName("Item có 1 phiên OPEN → cancel phiên rồi xóa item")
        void delete_item_with_open_auction() {
            Item item = service.addItem(SELLER_A, "X", "d", 100L, ItemType.ART);
            Auction openAuction = buildAuction(item.getId(), SELLER_A, AuctionStatus.OPEN);
            auctionDao.save(openAuction);

            service.deleteItemAsAdmin(item.getId());

            assertEquals(1, lifecycleService.cancelCount(),
                    "Phải gọi cancelAuction 1 lần cho phiên OPEN");
            assertTrue(auctionDao.findById(openAuction.getId()).isEmpty(),
                    "Phiên cũng bị xóa khỏi DAO");
            assertTrue(itemDao.findById(item.getId()).isEmpty());
        }

        @Test
        @DisplayName("Item có 1 phiên RUNNING → cancel phiên rồi xóa item")
        void delete_item_with_running_auction() {
            Item item = service.addItem(SELLER_A, "X", "d", 100L, ItemType.ART);
            Auction runningAuction = buildAuction(item.getId(), SELLER_A, AuctionStatus.RUNNING);
            auctionDao.save(runningAuction);

            service.deleteItemAsAdmin(item.getId());

            assertEquals(1, lifecycleService.cancelCount());
            assertTrue(itemDao.findById(item.getId()).isEmpty());
        }

        @Test
        @DisplayName("Item có phiên FINISHED → REJECT (bảo toàn lịch sử) — InvalidItemException")
        void delete_item_with_finished_auction_rejects() {
            Item item = service.addItem(SELLER_A, "X", "d", 100L, ItemType.ART);
            Auction finishedAuction = buildAuction(item.getId(), SELLER_A, AuctionStatus.FINISHED);
            auctionDao.save(finishedAuction);

            InvalidItemException ex = assertThrows(InvalidItemException.class,
                    () -> service.deleteItemAsAdmin(item.getId()));
            assertTrue(ex.getMessage().contains("lịch sử"));

            // State không thay đổi
            assertTrue(itemDao.findById(item.getId()).isPresent(),
                    "Item KHÔNG bị xóa");
            assertTrue(auctionDao.findById(finishedAuction.getId()).isPresent());
        }

        @Test
        @DisplayName("Item có phiên PAID → REJECT (bảo toàn lịch sử)")
        void delete_item_with_paid_auction_rejects() {
            Item item = service.addItem(SELLER_A, "X", "d", 100L, ItemType.ART);
            Auction paidAuction = buildAuction(item.getId(), SELLER_A, AuctionStatus.PAID);
            auctionDao.save(paidAuction);

            assertThrows(InvalidItemException.class,
                    () -> service.deleteItemAsAdmin(item.getId()));

            assertTrue(itemDao.findById(item.getId()).isPresent());
        }

        @Test
        @DisplayName("Item có 2 OPEN + 1 CANCELED → cancel 2 OPEN, delete cả 3 phiên + item")
        void delete_item_with_multiple_auctions() {
            Item item = service.addItem(SELLER_A, "X", "d", 100L, ItemType.ART);
            Auction o1 = buildAuction(item.getId(), SELLER_A, AuctionStatus.OPEN);
            Auction o2 = buildAuction(item.getId(), SELLER_A, AuctionStatus.OPEN);
            Auction cancelled = buildAuction(item.getId(), SELLER_A, AuctionStatus.CANCELED);
            auctionDao.save(o1);
            auctionDao.save(o2);
            auctionDao.save(cancelled);

            service.deleteItemAsAdmin(item.getId());

            assertEquals(2, lifecycleService.cancelCount(),
                    "Chỉ cancel 2 phiên OPEN, không cancel phiên đã CANCELED");
            assertTrue(itemDao.findById(item.getId()).isEmpty());
            assertEquals(0, auctionDao.findAll().size(),
                    "Tất cả 3 phiên bị xóa khỏi DAO");
        }

        @Test
        @DisplayName("Item không tồn tại → ItemNotFoundException")
        void delete_non_existent_item_throws() {
            assertThrows(ItemNotFoundException.class,
                    () -> service.deleteItemAsAdmin("ghost-id"));
        }

        @Test
        @DisplayName("itemId rỗng → InvalidItemException")
        void blank_item_id_throws() {
            assertThrows(InvalidItemException.class,
                    () -> service.deleteItemAsAdmin(""));
            assertThrows(InvalidItemException.class,
                    () -> service.deleteItemAsAdmin(null));
        }

        @Test
        @DisplayName("Admin xóa item của seller khác — KHÔNG check ownership (admin override)")
        void admin_can_delete_any_sellers_item() {
            Item item = service.addItem(SELLER_B, "X", "d", 100L, ItemType.ART);

            // Admin xóa, không truyền sellerId — pass
            assertDoesNotThrow(() -> service.deleteItemAsAdmin(item.getId()));
            assertTrue(itemDao.findById(item.getId()).isEmpty());
        }
    }

    // ════════════════════════════════════════════════════════════
    // 5. getItemsBySeller + getAllItems
    // ════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Query methods")
    class Queries {

        @Test
        @DisplayName("getItemsBySeller: trả về tất cả item của seller")
        void get_by_seller_returns_owned_items() {
            service.addItem(SELLER_A, "A1", "d", 100L, ItemType.ART);
            service.addItem(SELLER_A, "A2", "d", 200L, ItemType.ART);
            service.addItem(SELLER_B, "B1", "d", 300L, ItemType.ART);

            assertEquals(2, service.getItemsBySeller(SELLER_A).size());
            assertEquals(1, service.getItemsBySeller(SELLER_B).size());
        }

        @Test
        @DisplayName("getItemsBySeller: seller chưa có item → list rỗng")
        void get_by_seller_no_items() {
            assertTrue(service.getItemsBySeller("never-sold").isEmpty());
        }

        @Test
        @DisplayName("getItemsBySeller: sellerId rỗng → InvalidItemException")
        void get_by_seller_blank_throws() {
            assertThrows(InvalidItemException.class,
                    () -> service.getItemsBySeller(""));
            assertThrows(InvalidItemException.class,
                    () -> service.getItemsBySeller(null));
        }

        @Test
        @DisplayName("getAllItems: trả về toàn bộ item")
        void get_all_items() {
            assertEquals(0, service.getAllItems().size());

            service.addItem(SELLER_A, "X", "d", 100L, ItemType.ART);
            service.addItem(SELLER_B, "Y", "d", 200L, ItemType.ART);

            assertEquals(2, service.getAllItems().size());
        }
    }

    // ════════════════════════════════════════════════════════════
    // 6. Item polymorphism qua ItemFactory (cũng test trong ItemFactoryTest)
    // ════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Polymorphism: ItemFactory tạo đúng subclass")
    class Polymorphism {

        @Test
        @DisplayName("Tạo 3 loại item (Electronics/Art/Vehicle) — instanceOf đúng class")
        void create_all_item_types() {
            Item e = service.addItem(SELLER_A, "Phone", "d", 100L, ItemType.ELECTRONICS);
            Item a = service.addItem(SELLER_A, "Painting", "d", 200L, ItemType.ART);
            Item v = service.addItem(SELLER_A, "Bike", "d", 300L, ItemType.VEHICLE);

            assertInstanceOf(ElectronicsItem.class, e);
            assertInstanceOf(ArtItem.class, a);
            assertInstanceOf(VehicleItem.class, v);

            assertEquals(3, itemDao.findAll().size());
        }
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════

    private static final AtomicInteger AUCTION_COUNTER = new AtomicInteger();

    private Auction buildAuction(String itemId, String sellerId, AuctionStatus status) {
        LocalDateTime now = LocalDateTime.now();
        // Constructor cho phép set status trực tiếp — tận dụng để tạo auction
        // ở bất kỳ status nào mà không cần qua state machine transitions.
        LocalDateTime start = status == AuctionStatus.OPEN
                ? now.plusHours(1)
                : now.minusHours(1);
        LocalDateTime end = (status == AuctionStatus.FINISHED
                                || status == AuctionStatus.PAID
                                || status == AuctionStatus.CANCELED)
                ? now.minusMinutes(1)
                : now.plusHours(2);

        // Trick: nếu end <= start (do status trong quá khứ), điều chỉnh
        if (!end.isAfter(start)) {
            end = start.plusMinutes(30);
        }

        return new Auction(
                "auction-" + AUCTION_COUNTER.incrementAndGet(),
                itemId, sellerId,
                1_000_000L, 100_000L,
                status,                  // truyền thẳng status mong muốn
                start, end
        );
    }

    // ════════════════════════════════════════════════════════════
    // FAKES
    // ════════════════════════════════════════════════════════════

    static class FakeItemDao implements ItemDao {
        private final Map<String, Item> items = new HashMap<>();

        @Override public void save(Item item) { items.put(item.getId(), item); }
        @Override public Optional<Item> findById(String id) {
            return id == null ? Optional.empty() : Optional.ofNullable(items.get(id));
        }
        @Override public List<Item> findAll() { return new ArrayList<>(items.values()); }
        @Override public List<Item> findBySellerId(String sellerId) {
            return items.values().stream()
                    .filter(i -> i.getSellerId().equals(sellerId))
                    .collect(Collectors.toList());
        }
        @Override public void deleteById(String id) { items.remove(id); }
    }

    static class FakeAuctionDao implements AuctionDao {
        private final Map<String, Auction> store = new HashMap<>();

        @Override public List<Auction> findAll() { return new ArrayList<>(store.values()); }
        @Override public Optional<Auction> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }
        @Override public void save(Auction a) { store.put(a.getId(), a); }
        @Override public void update(Connection conn, Auction a) { store.put(a.getId(), a); }
        @Override public void deleteById(String id) { store.remove(id); }
    }

    /**
     * Fake lifecycle: theo dõi số lần cancel, mô phỏng việc cancel
     * (đổi status auction sang CANCELED).
     */
    static class FakeLifecycleService implements AuctionLifecycleService {
        private final FakeAuctionDao auctionDao;
        private final AtomicInteger cancelCount = new AtomicInteger();

        FakeLifecycleService(FakeAuctionDao auctionDao) { this.auctionDao = auctionDao; }

        int cancelCount() { return cancelCount.get(); }

        @Override
        public Auction cancelAuction(String auctionId, Role role) {
            cancelCount.incrementAndGet();
            Auction a = auctionDao.findById(auctionId).orElseThrow();
            a.cancel();
            auctionDao.save(a);
            return a;
        }

        @Override public Auction syncByTime(String auctionId) {
            return auctionDao.findById(auctionId).orElseThrow();
        }
        @Override public void scheduleStart(Auction auction) {}
        @Override public void scheduleClose(Auction auction) {}
        @Override public void rescheduleClose(Auction auction) {}
        @Override public void schedulePaymentTimeout(Auction auction) {}
        @Override public void shutdown() {}
    }
}
