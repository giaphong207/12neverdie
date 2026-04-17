package com.auction.client.context;

import com.auction.shared.model.User;

public final class ClientSession {
    private static User currentUser;
    private static String selectedAuctionId;

    //Để private constructor để ngăn không cho ai dùng từ khóa 'new ClientSession()'
    private ClientSession() {}

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static void clear() {
        currentUser = null;
        selectedAuctionId = null;
    }

    //tv3 và 4 dùng
    public static void setSelectedAuctionId(String auctionId) {
        selectedAuctionId = auctionId;
    }

    public static String getSelectedAuctionId() {
        return selectedAuctionId;
    }
}