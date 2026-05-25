package com.auction.server.DAO;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.auction.shared.model.User;
import com.auction.shared.model.Item;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AutoBidConfig;

public class AppDataStore implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<User> users = new ArrayList<>();
    private List<Item> items = new ArrayList<>();
    private List<Auction> auctions = new ArrayList<>();
    private List<AutoBidConfig> autoBidConfigs = new ArrayList<>();

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public List<Auction> getAuctions() {
        return auctions;
    }

    public void setAuctions(List<Auction> auctions) {
        this.auctions = auctions;
    }

    public List<AutoBidConfig> getAutoBidConfigs() {
        return autoBidConfigs;
    }

    public void setAutoBidConfigs(List<AutoBidConfig> autoBidConfigs) {
        this.autoBidConfigs = autoBidConfigs;
    }
}