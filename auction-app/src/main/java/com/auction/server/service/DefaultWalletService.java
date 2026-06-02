package com.auction.server.service;

import com.auction.server.dao.UserDao;
import com.auction.shared.exception.AppExceptions.DataAccessException;
import com.auction.shared.model.user.User;

import java.util.Optional;

public class DefaultWalletService implements WalletService {

    private final UserDao userDao;

    public DefaultWalletService(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public long getBalance(String userId) {
        Optional<User> userOpt = userDao.findById(userId);
        if (userOpt.isEmpty()) {
            throw new DataAccessException("Không tìm thấy user id=" + userId);
        }
        return userOpt.get().getBalance();
    }

    @Override
    public long deposit(String userId, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Số tiền nạp phải dương");
        return userDao.addBalance(userId, amount);   // atomic, hết lost update
    }

    @Override
    public boolean settlePayment(String winnerId, String sellerId, long amount) {
        if (amount <= 0) return true;                // không có gì để chuyển
        return userDao.transfer(winnerId, sellerId, amount);  // 1 transaction
    }
}
