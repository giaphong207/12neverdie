package com.auction.client.util;

import com.auction.shared.factory.UserFactory;
import com.auction.shared.model.auction.AuctionStatus;
import com.auction.shared.model.item.ItemType;
import com.auction.shared.model.user.Role;
import com.auction.shared.model.user.User;

/**
 * Chuyển enum tiếng Anh thành chuỗi tiếng Việt cho UI.
 */
public final class EnumFormatter {

    private EnumFormatter() {}

    public static String itemTypeVi(ItemType type) {
        if (type == null) return "";
        return switch (type) {
            case ELECTRONICS -> "Đồ điện tử";
            case ART -> "Tác phẩm nghệ thuật";
            case VEHICLE -> "Phương tiện";
        };
    }

    public static String auctionStatusVi(AuctionStatus status) {
        if (status == null) return "";
        return switch (status) {
            case OPEN -> "SẮP MỞ";
            case RUNNING -> "ĐANG DIỄN RA";
            case FINISHED -> "ĐÃ KẾT THÚC";
            case PAID -> "ĐÃ THANH TOÁN";
            case CANCELED -> "ĐÃ HỦY";
        };
    }

    /**
     * Trả về CSS class cho badge tương ứng với AuctionStatus.
     */
    public static String auctionStatusBadgeClass(AuctionStatus status) {
        if (status == null) return "badge-finished";
        return switch (status) {
            case OPEN -> "badge-open";
            case RUNNING -> "badge-running";
            case FINISHED -> "badge-finished";
            case PAID -> "badge-paid";
            case CANCELED -> "badge-canceled";
        };
    }
    public static String roleVi(Role role) {
        if (role == null) return "";
        return switch (role) {
            case ADMIN -> "Quản trị viên";
            case SELLER -> "Người bán";
            case BIDDER -> "Người đấu giá";
        };
    }
    public static String userTypeVi(User user) {
        if (user == null) return "";
        return roleVi(UserFactory.toRole(user));
    }

}