package com.auction.server.service;

import java.util.Optional;
import java.util.UUID;

import com.auction.server.dao.UserDao;
import com.auction.shared.model.Admin;
import com.auction.shared.model.Bidder;
import com.auction.shared.model.Role;
import com.auction.shared.model.Seller;
import com.auction.shared.model.User;

import org.mindrot.jbcrypt.BCrypt;

public class DefaultAuthService implements AuthService {

    private final UserDao userDao;

    public DefaultAuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public User login(String username, String password) {
        if (username == null || password == null) return null;

        Optional<User> userOpt = userDao.findByUsername(username);
        if (userOpt.isEmpty()) return null;

        User user = userOpt.get();
        String storedPwd = user.getPassword();

        // Backward compatible: hỗ trợ cả password plain text (data cũ) và BCrypt hash
        boolean matched;
        if (storedPwd.startsWith("$2a$") || storedPwd.startsWith("$2b$") || storedPwd.startsWith("$2y$")) {
            // BCrypt hash
            matched = BCrypt.checkpw(password, storedPwd);
        } else {
            // Plain text (data legacy)
            matched = storedPwd.equals(password);
        }

        return matched ? user : null;
    }

    @Override
    public User register(String username, String password, Role role) {
        if (usernameExists(username)) return null;

        // Hash password bằng BCrypt
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