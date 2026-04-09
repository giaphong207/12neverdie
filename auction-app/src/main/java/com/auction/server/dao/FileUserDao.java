package com.auction.server.dao;

import com.auction.shared.model.User;
import java.util.List;
import java.util.Optional;

public class FileUserDao implements UserDao {
    @Override
    public List<User> findAll() {
        return DataManager.getInstance().getStore().getUsers();
    }

    @Override
    public Optional<User> findById(String id) {
        // Tạm thời để return Optional.empty() vì class User của TV2 chưa code thuộc tính ID
        return Optional.empty();
    }

    @Override
    public void save(User user) {
        AppDataStore store = DataManager.getInstance().getStore();
        List<User> users = store.getUsers();
        users.add(user);
        store.setUsers(users);
        DataManager.getInstance().save(store);
    }
}