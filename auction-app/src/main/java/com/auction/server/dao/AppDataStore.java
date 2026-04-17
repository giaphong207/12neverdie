package com.auction.server.dao;

import com.auction.shared.model.User; // Import đúng đường dẫn trong project của bạn
// import com.auction.shared.model.Item; // (Sau này TV2 tạo xong Item thì bạn bỏ dấu comment đi)
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AppDataStore implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<User> users = new ArrayList<>();
    // private List<Item> items = new ArrayList<>();

    public List<User> getUsers() { return users; }
    public void setUsers(List<User> users) { this.users = users; }
}