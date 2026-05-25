package com.auction.server.seed;

import com.auction.server.DAO.AuctionDao;
import com.auction.server.DAO.ItemDao;
import com.auction.server.DAO.UserDao;
import com.auction.shared.model.*;
import com.auction.shared.pattern.ItemFactory;

import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Seed data demo khi DB trống.
 * Tạo: 1 admin, 1 seller, 2 bidder, 3 item, 3 auction.
 */
public class DatabaseSeeder {

    private final UserDao userDao;
    private final ItemDao itemDao;
    private final AuctionDao auctionDao;

    public DatabaseSeeder(UserDao userDao, ItemDao itemDao, AuctionDao auctionDao) {
        this.userDao = userDao;
        this.itemDao = itemDao;
        this.auctionDao = auctionDao;
    }

    public void seedIfEmpty() {
        if (!userDao.findAll().isEmpty()) {
            System.out.println("[Seeder] DB đã có user, bỏ qua seed.");
            return;
        }
        System.out.println("[Seeder] Seed demo data...");

        // Users (password được BCrypt hash)
        Admin admin = new Admin(uuid(), "admin", hash("admin123"));
        Seller seller = new Seller(uuid(), "seller1", hash("seller123"));
        Bidder bidder1 = new Bidder(uuid(), "bidder1", hash("bid123"));
        Bidder bidder2 = new Bidder(uuid(), "bidder2", hash("bid123"));
        for (User u : new User[]{admin, seller, bidder1, bidder2}) userDao.save(u);

        // Items
        Item phone = ItemFactory.createItem(ItemType.ELECTRONICS,
                uuid(), seller.getId(), "iPhone 15 Pro Max", "Mới 99%, fullbox", 28_000_000L);
        Item painting = ItemFactory.createItem(ItemType.ART,
                uuid(), seller.getId(), "Tranh Đông Hồ", "Bản gốc 1985", 5_000_000L);
        Item car = ItemFactory.createItem(ItemType.VEHICLE,
                uuid(), seller.getId(), "Honda Wave", "Đời 2020", 15_000_000L);
        for (Item i : new Item[]{phone, painting, car}) itemDao.save(i);

        // Auctions
        LocalDateTime now = LocalDateTime.now();
        Auction a1 = new Auction(uuid(), phone.getId(), seller.getId(),
                phone.getStartPrice(), 500_000L,
                AuctionStatus.RUNNING, now.minusMinutes(5), now.plusHours(1));
        Auction a2 = new Auction(uuid(), painting.getId(), seller.getId(),
                painting.getStartPrice(), 100_000L,
                AuctionStatus.RUNNING, now.minusMinutes(5), now.plusMinutes(2));
        Auction a3 = new Auction(uuid(), car.getId(), seller.getId(),
                car.getStartPrice(), 1_000_000L,
                AuctionStatus.OPEN, now.plusMinutes(30), now.plusHours(3));
        for (Auction a : new Auction[]{a1, a2, a3}) auctionDao.save(a);

        System.out.println("[Seeder] Seed xong: 4 users, 3 items, 3 auctions");
        System.out.println("[Seeder] Login demo: admin/admin123, seller1/seller123, bidder1/bid123, bidder2/bid123");
    }

    private static String uuid() { return UUID.randomUUID().toString(); }
    private static String hash(String pw) { return BCrypt.hashpw(pw, BCrypt.gensalt(10)); }
}