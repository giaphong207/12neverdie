package com.auction.server.service;

import java.util.Optional;
import java.util.UUID;

import com.auction.server.dao.UserDao;
import com.auction.shared.exception.AppExceptions.*;
import com.auction.shared.model.user.*;
import org.mindrot.jbcrypt.BCrypt;

public class DefaultAuthService implements AuthService {

    private final UserDao userDao;

    public DefaultAuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public User login(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new AuthenticationException("Tên đăng nhập không được rỗng");
        }
        if (password == null || password.isBlank()) {
            throw new AuthenticationException("Mật khẩu không được rỗng");
        }

        // Generic error message → không tiết lộ user tồn tại hay không
        Optional<User> userOpt = userDao.findByUsername(username);
        if (userOpt.isEmpty()) {
            throw new AuthenticationException("Sai tên đăng nhập hoặc mật khẩu");
        }

        User user = userOpt.get();
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new AuthenticationException("Sai tên đăng nhập hoặc mật khẩu");
        }

        return user;
    }

    @Override
    public User register(String username, String password, Role role) {
        if (username == null || username.isBlank()) {
            throw new InvalidInputException("Tên đăng nhập không được rỗng");
        }
        if (password == null || password.isBlank()) {
            throw new InvalidInputException("Mật khẩu không được rỗng");
        }
        if (role == null) {
            throw new InvalidInputException("Vai trò không được rỗng");
        }

        if (usernameExists(username)) {
            throw new DuplicateUsernameException(username);
        }

        String hashed = BCrypt.hashpw(password, BCrypt.gensalt(10));
        String newId = UUID.randomUUID().toString();
        User newUser = switch (role) {
            case ADMIN  -> new Admin(newId, username, hashed);
            case BIDDER -> new Bidder(newId, username, hashed);
            case SELLER -> new Seller(newId, username, hashed);
        };

        userDao.save(newUser);
        return newUser;
    }

    @Override
    public boolean usernameExists(String username) {
        return userDao.findByUsername(username).isPresent();
    }
}