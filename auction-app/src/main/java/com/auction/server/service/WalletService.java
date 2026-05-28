package com.auction.server.service;

public interface WalletService {
    long getBalance(String userId);
    long deposit(String userId, long amount);
}
