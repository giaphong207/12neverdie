package com.auction.client.context;

import com.auction.shared.model.user.User;

/**
 * Quản lý phiên làm việc của người dùng trên toàn ứng dụng.
 * Sử dụng Static Utility Class để truy cập nhanh từ mọi Controller.
 */
public final class ClientSession {
    //Dùng volatile để an toàn khi nhiều luồng cùng truy xuất
    private static volatile User currentUser;
    private static volatile String selectedAuctionId;

    //Để private constructor để ngăn không cho ai dùng từ khóa 'new ClientSession()'
    private ClientSession() {}

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    //Hàm tiện ích giúp UI kiểm tra trạng thái đăng nhập nhanh gọn
    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    //Xóa dữ liệu phiên khi đăng xuất
    public static void clear() {
        currentUser = null;
        selectedAuctionId = null;
    }

    //Dành cho TV3 và TV4 dùng để biết người dùng đang xem phòng đấu giá nào
    public static void setSelectedAuctionId(String auctionId) {
        selectedAuctionId = auctionId;
    }

    public static String getSelectedAuctionId() {
        return selectedAuctionId;
    }
}