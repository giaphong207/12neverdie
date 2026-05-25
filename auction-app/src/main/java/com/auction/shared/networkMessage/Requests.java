package com.auction.shared.networkMessage;

import com.auction.shared.model.ItemType;
import com.auction.shared.model.Role;

import java.io.Serializable;

public class Requests {
    public static record LoginRequest (String username, String password) implements Serializable {}
    public static record RegisterRequest(String username, String password, Role role) implements Serializable{}
    public static record GetSellerItemsRequest(String sellerId) implements Serializable{}
    public static record DeleteItemRequest(String itemId) implements Serializable {}
    public static record AddItemRequest(String name, String description, long startPrice,
                                 ItemType type, String sellerId) implements Serializable{}
    public static record UpdateItemRequest(String itemId, String name, String description,
                                    long startPrice, ItemType type, String sellerId) implements Serializable {}
    public static record BidRequest(String auctionId, String bidderId, long amount) implements Serializable {}
    public static record SetAutoBidRequest(String auctionId, String bidderId, long maxAmount, long increment) implements Serializable{}
    public static record SubscribeAuctionListRequest() implements Serializable {}
    public static record SubscribeAuctionRequest(String auctionId) implements Serializable{}

}
