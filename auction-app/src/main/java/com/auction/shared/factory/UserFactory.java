package com.auction.shared.factory;

import com.auction.shared.model.user.*;

public class UserFactory {
    public static User createUser(Role role, String id, String username, String password){
        return createUser(role, id, username, password, 0L);
    }

    public static User createUser(Role role, String id, String username, String password, long balance){
        if (role == null){
            throw new IllegalArgumentException("Phải điền loại User");
        }
        if (id == null || id.isBlank()){
            throw new IllegalArgumentException("Phải điền ID của User");
        }
        if (username == null || username.isBlank()){
            throw new IllegalArgumentException("Phải điền tên");
        }
        if (password == null|| password.isBlank()){
            throw new IllegalArgumentException("Phải điền mật khẩu");
        }
        // Factory Method: uỷ quyền khởi tạo cho chính Role (không còn switch)
        return role.create(id, username, password, balance);
    }
    public static Role toRole(User user){
        if (user instanceof Admin) return Role.ADMIN;
        if (user instanceof Seller) return Role.SELLER;
        if (user instanceof Bidder) return Role.BIDDER;
        throw new IllegalStateException("Không có loại User này");
    }
}
