package com.auction.shared.model.user;

public enum Role {
    ADMIN {
        @Override
        public User create(String id, String username, String password, long balance) {
            return new Admin(id, username, password, balance);
        }
    },
    SELLER {
        @Override
        public User create(String id, String username, String password, long balance) {
            return new Seller(id, username, password, balance);
        }
    },
    BIDDER {
        @Override
        public User create(String id, String username, String password, long balance) {
            return new Bidder(id, username, password, balance);
        }
    };

    /** Factory Method: mỗi vai trò tự quyết định khởi tạo lớp User nào (thay cho switch). */
    public abstract User create(String id, String username, String password, long balance);
}
