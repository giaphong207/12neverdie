package com.auction.server.service;

public interface WalletService {
    long getBalance(String userId);
    long deposit(String userId, long amount);

    /**
     * Thanh toán khi phiên kết thúc: trừ {@code amount} của winner, cộng cho seller.
     * @return true nếu thành công; false nếu winner KHÔNG đủ tiền (không trừ/cộng gì).
     */
    boolean settlePayment(String winnerId, String sellerId, long amount);
}
