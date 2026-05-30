package com.auction.shared.networkMessage;

import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.user.Role;
import com.auction.shared.model.user.User;

import java.io.Serializable;
import java.util.List;

public class Results {
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

    public sealed interface AddItemResult extends Serializable
            permits AddItemResult.Success, AddItemResult.Failure {

        record Success(Item item) implements AddItemResult {}
        record Failure(String reason) implements AddItemResult {}
    }

    public sealed interface UpdateItemResult extends Serializable
            permits UpdateItemResult.Success, UpdateItemResult.Failure {

        record Success(Item item) implements UpdateItemResult {}
        record Failure(String reason) implements UpdateItemResult {}
    }

    public sealed interface DeleteItemResult extends Serializable
            permits DeleteItemResult.Success, DeleteItemResult.Failure {

        record Success() implements DeleteItemResult {}
        record Failure(String reason) implements DeleteItemResult {}
    }

    public sealed interface GetSellerItemsResult extends Serializable
            permits GetSellerItemsResult.Success, GetSellerItemsResult.Failure {

        record Success(List<Item> items) implements GetSellerItemsResult {}
        record Failure(String reason) implements GetSellerItemsResult {}
    }

    public sealed interface BidResult extends Serializable
            permits BidResult.Success, BidResult.Failure {

        record Success(Auction auction) implements BidResult {}
        record Failure(String reason) implements BidResult {}
    }

    public sealed interface GetBalanceResult extends Serializable
            permits GetBalanceResult.Success, GetBalanceResult.Failure {

        record Success(long balance) implements GetBalanceResult {}
        record Failure(String reason) implements GetBalanceResult {}
    }

    public sealed interface DepositResult extends Serializable
            permits DepositResult.Success, DepositResult.Failure {

        record Success(long newBalance) implements DepositResult {}
        record Failure(String reason) implements DepositResult {}
    }

    public static record SetAutoBidResponse(boolean success, String message) implements Serializable {}
    public static record UserRow(String username, Role role) implements Serializable {}

    public sealed interface GetAllUsersResult extends Serializable
            permits GetAllUsersResult.Success, GetAllUsersResult.Failure {

        record Success(List<UserRow> users) implements GetAllUsersResult {}
        record Failure(String reason) implements GetAllUsersResult {}
    }
    public static record ErrorMessage(String message) implements Serializable {}
}
