package com.auction.server.service;

import com.auction.server.dao.UserDao;
import com.auction.shared.exception.AppExceptions.DataAccessException;
import com.auction.shared.model.user.Bidder;
import com.auction.shared.model.user.Seller;
import com.auction.shared.model.user.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WalletService - ví tiền: getBalance / deposit / settlePayment")
class WalletServiceTest {

    private DefaultWalletService walletService;
    private FakeUserDao userDao;

    private static final String BIDDER_ID = "u-bidder-1";
    private static final String SELLER_ID = "u-seller-1";

    @BeforeEach
    void setUp() {
        userDao = new FakeUserDao();
        userDao.save(new Bidder(BIDDER_ID, "bidder1", "pwd", 1_000_000L));
        userDao.save(new Seller(SELLER_ID, "seller1", "pwd"));
        walletService = new DefaultWalletService(userDao);
    }

    @Test
    @DisplayName("getBalance trả đúng số dư của user")
    void get_balance_returns_user_balance() {
        assertEquals(1_000_000L, walletService.getBalance(BIDDER_ID));
        assertEquals(0L, walletService.getBalance(SELLER_ID));
    }

    @Test
    @DisplayName("getBalance user không tồn tại → DataAccessException")
    void get_balance_unknown_user_throws() {
        assertThrows(DataAccessException.class,
                () -> walletService.getBalance("ghost-user"));
    }

    @Test
    @DisplayName("Deposit số tiền dương → cộng vào ví, trả về số dư mới")
    void deposit_valid_amount_increases_balance() {
        long newBalance = walletService.deposit(BIDDER_ID, 500_000L);
        assertEquals(1_500_000L, newBalance);
        assertEquals(1_500_000L, walletService.getBalance(BIDDER_ID));
    }

    @Test
    @DisplayName("Deposit nhiều lần → cộng tích lũy")
    void deposit_multiple_times_accumulates() {
        walletService.deposit(BIDDER_ID, 100_000L);
        walletService.deposit(BIDDER_ID, 200_000L);
        walletService.deposit(BIDDER_ID, 300_000L);
        assertEquals(1_600_000L, walletService.getBalance(BIDDER_ID));
    }

    @Test
    @DisplayName("Deposit số tiền âm → IllegalArgumentException")
    void deposit_negative_amount_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> walletService.deposit(BIDDER_ID, -1000L));
    }

    @Test
    @DisplayName("Deposit số tiền 0 → IllegalArgumentException")
    void deposit_zero_amount_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> walletService.deposit(BIDDER_ID, 0L));
    }

    @Test
    @DisplayName("Deposit cho user không tồn tại → ném exception từ DAO")
    void deposit_unknown_user_throws() {
        assertThrows(IllegalStateException.class,
                () -> walletService.deposit("ghost-user", 100_000L));
    }

    @Test
    @DisplayName("Settle khi winner đủ tiền → trừ winner, cộng seller, trả true")
    void settle_with_sufficient_balance_transfers_money() {
        long sellerBefore = walletService.getBalance(SELLER_ID);
        long bidderBefore = walletService.getBalance(BIDDER_ID);

        boolean result = walletService.settlePayment(BIDDER_ID, SELLER_ID, 300_000L);

        assertTrue(result);
        assertEquals(bidderBefore - 300_000L, walletService.getBalance(BIDDER_ID));
        assertEquals(sellerBefore + 300_000L, walletService.getBalance(SELLER_ID));
    }

    @Test
    @DisplayName("Settle khi winner KHÔNG đủ tiền → trả false, không trừ/cộng")
    void settle_with_insufficient_balance_returns_false() {
        long bidderBefore = walletService.getBalance(BIDDER_ID);
        long sellerBefore = walletService.getBalance(SELLER_ID);

        boolean result = walletService.settlePayment(BIDDER_ID, SELLER_ID, 5_000_000L);

        assertFalse(result);
        assertEquals(bidderBefore, walletService.getBalance(BIDDER_ID));
        assertEquals(sellerBefore, walletService.getBalance(SELLER_ID));
    }

    @Test
    @DisplayName("Settle với amount = 0 → trả true (không có gì để chuyển)")
    void settle_zero_amount_returns_true() {
        long bidderBefore = walletService.getBalance(BIDDER_ID);
        long sellerBefore = walletService.getBalance(SELLER_ID);

        boolean result = walletService.settlePayment(BIDDER_ID, SELLER_ID, 0L);

        assertTrue(result);
        assertEquals(bidderBefore, walletService.getBalance(BIDDER_ID));
        assertEquals(sellerBefore, walletService.getBalance(SELLER_ID));
    }

    @Test
    @DisplayName("Settle với amount âm → coi như 0, trả true")
    void settle_negative_amount_returns_true() {
        boolean result = walletService.settlePayment(BIDDER_ID, SELLER_ID, -100L);
        assertTrue(result);
    }

    @Test
    @DisplayName("Settle đúng tổng tiền: tiền winner mất = tiền seller nhận")
    void settle_conserves_money() {
        long totalBefore = walletService.getBalance(BIDDER_ID) + walletService.getBalance(SELLER_ID);
        walletService.settlePayment(BIDDER_ID, SELLER_ID, 250_000L);
        long totalAfter = walletService.getBalance(BIDDER_ID) + walletService.getBalance(SELLER_ID);
        assertEquals(totalBefore, totalAfter);
    }

    @Test
    @DisplayName("Settle nhiều lần liên tiếp — đúng trạng thái cuối")
    void settle_multiple_times_correct_final_state() {
        walletService.deposit(BIDDER_ID, 9_000_000L);
        assertEquals(10_000_000L, walletService.getBalance(BIDDER_ID));

        walletService.settlePayment(BIDDER_ID, SELLER_ID, 3_000_000L);
        walletService.settlePayment(BIDDER_ID, SELLER_ID, 2_000_000L);

        assertEquals(5_000_000L, walletService.getBalance(BIDDER_ID));
        assertEquals(5_000_000L, walletService.getBalance(SELLER_ID));
    }

    // ===== FAKE DAO =====
    static class FakeUserDao implements UserDao {
        private final Map<String, User> usersById = new HashMap<>();

        @Override public void save(User user) { usersById.put(user.getId(), user); }

        @Override public Optional<User> findById(String id) {
            return id == null ? Optional.empty() : Optional.ofNullable(usersById.get(id));
        }

        @Override public Optional<User> findByUsername(String username) {
            if (username == null) return Optional.empty();
            return usersById.values().stream()
                    .filter(u -> u.getUsername().equals(username))
                    .findFirst();
        }

        @Override public List<User> findAll() { return new ArrayList<>(usersById.values()); }

        @Override
        public long updateBalance(String userId, long newBalance) {
            User u = usersById.get(userId);
            if (u == null) throw new IllegalStateException("user not found: " + userId);
            u.setBalance(newBalance);
            return newBalance;
        }

        @Override
        public long addBalance(String userId, long delta) {
            User u = usersById.get(userId);
            if (u == null) throw new IllegalStateException("user not found: " + userId);
            u.setBalance(u.getBalance() + delta);
            return u.getBalance();
        }

        @Override
        public boolean transfer(String fromId, String toId, long amount) {
            User from = usersById.get(fromId);
            User to   = usersById.get(toId);
            if (from == null || to == null) throw new IllegalStateException("user not found");
            if (from.getBalance() < amount) return false;
            from.setBalance(from.getBalance() - amount);
            to.setBalance(to.getBalance() + amount);
            return true;
        }
    }
}
