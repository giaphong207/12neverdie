package com.auction.server.dao;

import java.io.Serializable; // Import đúng đường dẫn trong project của bạn
import java.util.ArrayList;
import java.util.List;

import com.auction.shared.model.User;

public class AppDataStore implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<User> users = new ArrayList<>();
    // private List<Item> items = new ArrayList<>();

    public List<User> getUsers() { return users; }
    public void setUsers(List<User> users) { this.users = users; }
}