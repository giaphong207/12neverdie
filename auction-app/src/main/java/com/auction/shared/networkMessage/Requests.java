package com.auction.shared.networkMessage;

import com.auction.shared.model.item.ItemType;
import com.auction.shared.model.user.Role;
import com.auction.shared.exception.AppExceptions.*;
import java.io.Serializable;

public class Requests {
    public static record LoginRequest(String username, String password) implements Serializable {
        public LoginRequest {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("username không được rỗng");
            }
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException("password không được rỗng");
            }
        }
    }
    public static record RegisterRequest(String username, String password, Role role) implements Serializable {
        public RegisterRequest {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("username không được rỗng");
            }
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException("password không được rỗng");
            }
            if (role == null) {
                throw new IllegalArgumentException("role không được rỗng");
            }
        }
    }
    public static record GetSellerItemsRequest(String sellerId) implements Serializable {
        public GetSellerItemsRequest {
            if (sellerId == null || sellerId.isBlank()) {
                throw new InvalidItemException("sellerId không được rỗng");
            }
        }
    }
    public static record DeleteItemRequest(String itemId, String sellerId) implements Serializable {
        public DeleteItemRequest {
            if (itemId == null || itemId.isBlank()) {
                throw new InvalidItemException("itemId không được rỗng");
            }
            if (sellerId == null || sellerId.isBlank()) {
                throw new InvalidItemException("sellerId không được rỗng");
            }
        }
    }
    public static record AddItemRequest(String name, String description, long startPrice,
                                        ItemType type, String sellerId) implements Serializable {
        public AddItemRequest {
            if (name == null || name.isBlank()) {
                throw new InvalidItemException("Tên sản phẩm không được rỗng");
            }
            if (description == null || description.isBlank()) {
                throw new InvalidItemException("Mô tả không được rỗng");
            }
            if (startPrice <= 0) {
                throw new InvalidItemException("Giá khởi điểm phải lớn hơn 0");
            }
            if (type == null) {
                throw new InvalidItemException("Loại sản phẩm không được rỗng");
            }
            if (sellerId == null || sellerId.isBlank()) {
                throw new InvalidItemException("sellerId không được rỗng");
            }
        }
    }
    public static record UpdateItemRequest(String itemId, String name, String description,
                                           long startPrice, ItemType type, String sellerId) implements Serializable {
        public UpdateItemRequest {
            if (itemId == null || itemId.isBlank()) {
                throw new InvalidItemException("itemId không được rỗng");
            }
            if (name == null || name.isBlank()) {
                throw new InvalidItemException("Tên sản phẩm không được rỗng");
            }
            if (description == null || description.isBlank()) {
                throw new InvalidItemException("Mô tả không được rỗng");
            }
            if (startPrice < 0) {
                throw new InvalidItemException("Giá khởi điểm không được âm");
            }
            if (type == null) {
                throw new InvalidItemException("Loại sản phẩm không được rỗng");
            }
            if (sellerId == null || sellerId.isBlank()) {
                throw new InvalidItemException("sellerId không được rỗng");
            }
        }
    }
    public static record BidRequest(String auctionId, String bidderId, long amount) implements Serializable {
        public BidRequest {
            if (auctionId == null || auctionId.isBlank()) {
                throw new InvalidBidException("auctionId không được rỗng");
            }
            if (bidderId == null || bidderId.isBlank()) {
                throw new InvalidBidException("bidderId không được rỗng");
            }
            if (amount <= 0) {
                throw new InvalidBidException("Số tiền phải dương");
            }
        }
    }
    public static record SetAutoBidRequest(String auctionId, String bidderId, long maxAmount, long increment) implements Serializable {
        public SetAutoBidRequest {
            if (auctionId == null || auctionId.isBlank()) {
                throw new InvalidBidException("auctionId không được rỗng");
            }
            if (bidderId == null || bidderId.isBlank()) {
                throw new InvalidBidException("bidderId không được rỗng");
            }
            if (maxAmount <= 0) {
                throw new InvalidBidException("maxAmount phải dương");
            }
            if (increment <= 0) {
                throw new InvalidBidException("increment phải dương");
            }
        }
    }
    public static record SubscribeAuctionListRequest() implements Serializable {}
    public static record SubscribeAuctionRequest(String auctionId) implements Serializable{}

}
