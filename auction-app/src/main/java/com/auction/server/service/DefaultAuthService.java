package com.auction.server.service;

import java.util.Optional;
import java.util.UUID;

import com.auction.server.dao.FileUserDao;
import com.auction.server.dao.UserDao;
import com.auction.shared.model.Role;
import com.auction.shared.model.User; //Dùng để tạo ID ngẫu nhiên

public class DefaultAuthService implements AuthService {

    //Service cần gọi DAO để thao tác với file
    private final UserDao userDao;

    public DefaultAuthService() {
        this.userDao = new FileUserDao();
        seedAdmin();
    }

    @Override
    public User login(String username, String password) {
        //Nhờ DAO tìm user theo username
        Optional<User> userOpt = userDao.findByUsername(username);
        
        //Nếu tìm thấy và mật khẩu khớp thì trả về User đó
        if (userOpt.isPresent() && userOpt.get().getPassword().equals(password)) {
            return userOpt.get();
        }
        
        //Nếu sai thì trả về null -> Đăng nhập thất bại
        return null; 
    }

    @Override
    public User register(String username, String password, Role role) {
        //Kiểm tra trùng tên
        if (usernameExists(username)) {
            return null; // Đã tồn tại -> Đăng ký thất bại
        }
        
        //Tạo ID ngẫu nhiên cho User mới
        String newId = UUID.randomUUID().toString();
     
        User newUser = new User(newId, username, password, role);
        
        //Nhờ DAO lưu xuống file
        userDao.save(newUser);
        
        return newUser;
    }

    @Override
    public boolean usernameExists(String username) {
        return userDao.findByUsername(username).isPresent(); //isPresent() sẽ trả về true nếu tìm thấy, false nếu không thấy
    }

    private void seedAdmin() {
        if (!usernameExists("admin")) {
            User adminUser = new Admin(
                UUID.randomUUID().toString(),
                "admin", //username cố định
                "admin123" //password cố định
            );
            userDao.save(adminUser);
        }
    }
}