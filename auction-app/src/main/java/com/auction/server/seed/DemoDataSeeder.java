package com.auction.server.seed;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.auction.shared.model.Admin;
import com.auction.shared.model.ArtItem;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;
import com.auction.shared.model.Bid;
import com.auction.shared.model.Bidder;
import com.auction.shared.model.ElectronicsItem;
import com.auction.shared.model.Item;
import com.auction.shared.model.Seller;
import com.auction.shared.model.User;
import com.auction.shared.model.VehicleItem;

/**
 * Tạo dữ liệu demo cho 4 scenario tuần 5.
 *
 * <p>Gọi từ DataManager.seedIfMissing() — chỉ chạy 1 lần khi database.dat rỗng.
 *
 * <p>Tài khoản mặc định (thêm vào README):
 * <pre>
 *   admin   / admin123
 *   seller1 / 123456
 *   bidder1 / 123456
 *   bidder2 / 123456
 *   bidder3 / 123456
 * </pre>
 */
public final class DemoDataSeeder {

    // ── ID cố định — không đổi giữa các lần chạy ─────────────────────────────
    public static final String ID_ADMIN   = "user-admin-01";
    public static final String ID_SELLER  = "user-seller-01";
    public static final String ID_BIDDER1 = "user-bidder-01";
    public static final String ID_BIDDER2 = "user-bidder-02";
    public static final String ID_BIDDER3 = "user-bidder-03";

    public static final String ID_ITEM_LAPTOP    = "item-laptop-01";
    public static final String ID_ITEM_PAINTING  = "item-painting-01";
    public static final String ID_ITEM_MOTORBIKE = "item-moto-01";
    public static final String ID_ITEM_HEADPHONE = "item-headphone-01";

    public static final String ID_AUCTION_CHART       = "auction-chart-demo";
    public static final String ID_AUCTION_ANTISNIPING = "auction-antisniping-demo";
    public static final String ID_AUCTION_AUTOBID     = "auction-autobid-demo";
    public static final String ID_AUCTION_STRESS      = "auction-stress-demo";

    private DemoDataSeeder() {}

    // ══════════════════════════════════════════════════════════════
    // USERS
    // ══════════════════════════════════════════════════════════════

    public static User createAdmin() {
        return new Admin(ID_ADMIN, "admin", "admin123");
    }

    public static User createSeller() {
        return new Seller(ID_SELLER, "seller1", "123456");
    }

    public static User createBidder1() {
        return new Bidder(ID_BIDDER1, "bidder1", "123456");
    }

    public static User createBidder2() {
        return new Bidder(ID_BIDDER2, "bidder2", "123456");
    }

    public static User createBidder3() {
        return new Bidder(ID_BIDDER3, "bidder3", "123456");
    }

    public static List<User> allUsers() {
        List<User> list = new ArrayList<>();
        list.add(createAdmin());
        list.add(createSeller());
        list.add(createBidder1());
        list.add(createBidder2());
        list.add(createBidder3());
        return list;
    }

    // ══════════════════════════════════════════════════════════════
    // ITEMS
    // Constructor thực tế: (id, sellerId, name, description, startPrice)
    // ══════════════════════════════════════════════════════════════

    public static Item createLaptop() {
        return new ElectronicsItem(
                ID_ITEM_LAPTOP,
                ID_SELLER,
                "Laptop Dell XPS 15 2024",
                "CPU Intel i9-13900H, RAM 32GB DDR5, SSD 1TB NVMe, màn hình OLED 4K",
                15_000_000L
        );
    }

    public static Item createPainting() {
        return new ArtItem(
                ID_ITEM_PAINTING,
                ID_SELLER,
                "Tranh sơn dầu Phong cảnh Hạ Long",
                "Kích thước 80x60cm, vẽ tay hoàn toàn, có chứng nhận tác giả",
                5_000_000L
        );
    }

    public static Item createMotorbike() {
        return new VehicleItem(
                ID_ITEM_MOTORBIKE,
                ID_SELLER,
                "Honda CB650R 2023",
                "Màu đỏ đen, đã đi 5.200km, còn bảo hành hãng đến 2025",
                80_000_000L
        );
    }

    public static Item createHeadphone() {
        return new ElectronicsItem(
                ID_ITEM_HEADPHONE,
                ID_SELLER,
                "Sony WH-1000XM5",
                "Chống ồn ANC, pin 30 giờ, Bluetooth 5.2",
                6_000_000L
        );
    }

    public static List<Item> allItems() {
        List<Item> list = new ArrayList<>();
        list.add(createLaptop());
        list.add(createPainting());
        list.add(createMotorbike());
        list.add(createHeadphone());
        return list;
    }

    // ══════════════════════════════════════════════════════════════
    // SCENARIO 1 — CHART DEMO
    // Auction có sẵn 5 bid → mở detail thấy chart ngay
    //
    // Auction: (id, itemId, sellerId, startPrice, minIncrement,
    //           status, startTime, endTime)
    // Bid:     (id, auctionId, bidderId, amount, createdAt)
    // ══════════════════════════════════════════════════════════════

    public static Auction createChartDemoAuction() {
        LocalDateTime now = LocalDateTime.now();

        Auction auction = new Auction(
                ID_AUCTION_CHART,
                ID_ITEM_LAPTOP,
                ID_SELLER,
                10_000_000L,
                200_000L,
                AuctionStatus.RUNNING,
                now.minusMinutes(10),
                now.plusHours(2)
        );

        LocalDateTime base = now.minusMinutes(8);

        auction.addBid(new Bid(
                UUID.randomUUID().toString(),
                ID_AUCTION_CHART,
                ID_BIDDER1,
                10_200_000L,
                base
        ));
        auction.addBid(new Bid(
                UUID.randomUUID().toString(),
                ID_AUCTION_CHART,
                ID_BIDDER2,
                10_400_000L,
                base.plusSeconds(30)
        ));
        auction.addBid(new Bid(
                UUID.randomUUID().toString(),
                ID_AUCTION_CHART,
                ID_BIDDER1,
                10_600_000L,
                base.plusSeconds(60)
        ));
        auction.addBid(new Bid(
                UUID.randomUUID().toString(),
                ID_AUCTION_CHART,
                ID_BIDDER3,
                10_800_000L,
                base.plusSeconds(90)
        ));
        auction.addBid(new Bid(
                UUID.randomUUID().toString(),
                ID_AUCTION_CHART,
                ID_BIDDER2,
                11_000_000L,
                base.plusSeconds(120)
        ));

        return auction;
    }

    // ══════════════════════════════════════════════════════════════
    // SCENARIO 2 — ANTI-SNIPING DEMO
    // endTime = now + 20s → đặt bid ngay là trigger anti-sniping
    //
    // LƯU Ý: Xóa database.dat và restart server trước khi demo
    // để endTime được tính lại từ thời điểm hiện tại.
    // ══════════════════════════════════════════════════════════════

    public static Auction createAntiSnipingDemoAuction() {
        LocalDateTime now = LocalDateTime.now();

        Auction auction = new Auction(
                ID_AUCTION_ANTISNIPING,
                ID_ITEM_PAINTING,
                ID_SELLER,
                3_000_000L,
                100_000L,
                AuctionStatus.RUNNING,
                now.minusMinutes(30),
                now.plusSeconds(20)   // CHỈ CÒN 20 GIÂY
        );

        auction.addBid(new Bid(
                UUID.randomUUID().toString(),
                ID_AUCTION_ANTISNIPING,
                ID_BIDDER1,
                3_100_000L,
                now.minusMinutes(15)
        ));

        return auction;
    }

    // ══════════════════════════════════════════════════════════════
    // SCENARIO 3 — AUTO-BID DEMO
    // 3 bidder sẵn sàng → vào app cấu hình auto-bid qua dialog
    // Không seed AutoBidConfig — bidder tự cấu hình trong app
    // ══════════════════════════════════════════════════════════════

    public static Auction createAutoBidDemoAuction() {
        LocalDateTime now = LocalDateTime.now();

        return new Auction(
                ID_AUCTION_AUTOBID,
                ID_ITEM_MOTORBIKE,
                ID_SELLER,
                70_000_000L,
                500_000L,
                AuctionStatus.RUNNING,
                now.minusMinutes(5),
                now.plusHours(1)
        );
    }

    // ══════════════════════════════════════════════════════════════
    // SCENARIO 4 — STRESS / GENERAL DEMO
    // Nhiều auction có bid cũ → AuctionList trông sống động
    // ══════════════════════════════════════════════════════════════

    public static Auction createStressDemoAuction() {
        LocalDateTime now = LocalDateTime.now();

        Auction auction = new Auction(
                ID_AUCTION_STRESS,
                ID_ITEM_HEADPHONE,
                ID_SELLER,
                5_000_000L,
                100_000L,
                AuctionStatus.RUNNING,
                now.minusHours(1),
                now.plusHours(3)
        );

        auction.addBid(new Bid(
                UUID.randomUUID().toString(),
                ID_AUCTION_STRESS,
                ID_BIDDER1,
                5_100_000L,
                now.minusMinutes(50)
        ));

        auction.addBid(new Bid(
                UUID.randomUUID().toString(),
                ID_AUCTION_STRESS,
                ID_BIDDER2,
                5_200_000L,
                now.minusMinutes(30)
        ));

        return auction;
    }

    public static List<Auction> allAuctions() {
        List<Auction> list = new ArrayList<>();
        list.add(createChartDemoAuction());
        list.add(createAntiSnipingDemoAuction());
        list.add(createAutoBidDemoAuction());
        list.add(createStressDemoAuction());
        return list;
    }
}