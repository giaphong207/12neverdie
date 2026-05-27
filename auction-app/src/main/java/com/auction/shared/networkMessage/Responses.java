package com.auction.shared.networkMessage;

import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.user.User;

import java.io.Serializable;
import java.util.List;

public class Responses {
    public static record LoginResponse(boolean success, String message, User user) implements Serializable {}
    public static record RegisterResponse(boolean success, String message, User user) implements Serializable {}
    public sealed interface LoginResult extends Serializable
            permits LoginResult.Success, LoginResult.Failure {

        record Success(User user) implements LoginResult {}
        record Failure(String reason) implements LoginResult {}
    }

    public sealed interface RegisterResult extends Serializable
            permits RegisterResult.Success, RegisterResult.Failure {

        record Success(User user) implements RegisterResult {}
        record Failure(String reason) implements RegisterResult {}
    }
    public static record GetSellerItemsResponse(boolean success, String message, List<Item> items) implements Serializable {}
    public static record DeleteItemResponse(boolean success, String message) implements Serializable {}
    public static record AddItemResponse(boolean success, String message, Item item) implements Serializable {}
    public static record UpdateItemResponse(boolean success, String message, Item item) implements Serializable {}
    public static record BidResponse(boolean success, String message, Auction updatedAuction) implements Serializable {}
    public sealed interface BidResult extends Serializable
            permits BidResult.Success, BidResult.Failure {

        record Success(Auction auction) implements BidResult {}
        record Failure(String reason) implements BidResult {}
    }
    public static record SetAutoBidResponse(boolean success, String message) implements Serializable {}
    public static record ErrorMessage(String message) implements Serializable {}
}
