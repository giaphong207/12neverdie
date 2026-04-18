package com.auction.server.dao;

import java.util.List;
import java.util.Optional;

import com.auction.shared.model.User;

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
    public Optional<User> findByUsername(String username) {
        // làm chức năng Đăng Nhập
        return findAll().stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst();
    }

    @Override
    public void save(User user) {
        AppDataStore store = DataManager.getInstance().getStore();
        List<User> users = store.getUsers();
        
        // Cập nhật: xóa user cũ nếu trùng ID 
        users.removeIf(u -> u.getId() != null && u.getId().equals(user.getId()));
        
        users.add(user);
        store.setUsers(users);
        DataManager.getInstance().save(store);
    }
}