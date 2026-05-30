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
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền nạp phải dương");
        }
        Optional<User> userOpt = userDao.findById(userId);
        if (userOpt.isEmpty()) {
            throw new DataAccessException("Không tìm thấy user id=" + userId);
        }
        long current = userOpt.get().getBalance();
        long updated = current + amount;
        userDao.updateBalance(userId, updated);
        return updated;
    }

    @Override
    public boolean settlePayment(String winnerId, String sellerId, long amount) {
        if (amount <= 0) {
            return true; // không có gì để chuyển
        }
        User winner = userDao.findById(winnerId)
                .orElseThrow(() -> new DataAccessException("Không tìm thấy winner id=" + winnerId));
        if (winner.getBalance() < amount) {
            return false; // winner không đủ tiền → không trừ/cộng gì
        }
        User seller = userDao.findById(sellerId)
                .orElseThrow(() -> new DataAccessException("Không tìm thấy seller id=" + sellerId));

        userDao.updateBalance(winnerId, winner.getBalance() - amount);
        userDao.updateBalance(sellerId, seller.getBalance() + amount);
        return true;
    }
}
