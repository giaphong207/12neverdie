package com.auction.server.dao;

import com.auction.shared.model.User;
import java.util.List;
import java.util.Optional;

public class FileUserDao implements UserDao {
    
    @Override
    public List<User> findAll() {
        // Dùng DataManager của TV3
        return DataManager.getInstance().getStore().getUsers();
    }

    @Override
    public Optional<User> findById(String id) {
        // Tạm thời để return Optional.empty() vì class User của TV2 chưa code thuộc tính ID
        return Optional.empty();
    }

    @Override
    public Optional<User> findByUsername(String username) {
        // Thầy giữ lại hàm này của em để em làm chức năng Đăng Nhập
        return findAll().stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst();
    }

    @Override
    public void save(User user) {
        // Dùng DataManager xịn của TV3
        AppDataStore store = DataManager.getInstance().getStore();
        List<User> users = store.getUsers();
        
        // Cập nhật: xóa user cũ nếu trùng ID (kết hợp logic cũ của em cho an toàn)
        users.removeIf(u -> u.getId() != null && u.getId().equals(user.getId()));
        
        users.add(user);
        store.setUsers(users);
        DataManager.getInstance().save(store);
    }
}