package com.auction.server.service;

import com.auction.server.dao.UserDao;
import com.auction.shared.factory.UserFactory;
import com.auction.shared.model.user.Role;
import com.auction.shared.model.user.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuthService - login/register với BCrypt")
class AuthServiceTest {

    private DefaultAuthService authService;
    private FakeUserDao userDao;

    @BeforeEach
    void setUp() {
        userDao = new FakeUserDao();
        authService = new DefaultAuthService(userDao);
    }

    @Test
    @DisplayName("Register tạo user mới với BCrypt hash")
    void register_creates_user_with_bcrypt() {
        User user = authService.register("alice", "pwd123", Role.BIDDER);
        assertNotNull(user);
        assertEquals("alice", user.getUsername());
        assertEquals(Role.BIDDER, UserFactory.toRole(user));
        assertTrue(user.getPassword().startsWith("$2"),
                "Password phải là BCrypt hash");
    }

    @Test
    @DisplayName("Register username trùng trả null")
    void register_duplicate_returns_null() {
        authService.register("bob", "pwd", Role.BIDDER);
        assertNull(authService.register("bob", "other", Role.SELLER));
    }

    @Test
    @DisplayName("Login đúng credentials trả về user")
    void login_correct_returns_user() {
        authService.register("charlie", "secret", Role.SELLER);
        User result = authService.login("charlie", "secret");
        assertNotNull(result);
        assertEquals(Role.SELLER, UserFactory.toRole(result));
    }

    @Test
    @DisplayName("Login sai password trả null")
    void login_wrong_password_returns_null() {
        authService.register("dave", "right", Role.BIDDER);
        assertNull(authService.login("dave", "wrong"));
    }

    @Test
    @DisplayName("Login user không tồn tại trả null")
    void login_unknown_user_returns_null() {
        assertNull(authService.login("ghost", "anything"));
    }

    @Test
    @DisplayName("Login với input null/empty không crash")
    void login_null_inputs() {
        assertNull(authService.login(null, "pwd"));
        assertNull(authService.login("user", null));
        assertNull(authService.login(null, null));
    }

    @Test
    @DisplayName("usernameExists check chính xác")
    void username_exists_check() {
        assertFalse(authService.usernameExists("nobody"));
        authService.register("eve", "p", Role.BIDDER);
        assertTrue(authService.usernameExists("eve"));
    }

    @Test
    @DisplayName("Register 3 role tạo đúng")
    void register_all_three_roles() {
        assertEquals(Role.BIDDER, UserFactory.toRole(authService.register("b", "p", Role.BIDDER)));
        assertEquals(Role.SELLER, UserFactory.toRole(authService.register("s", "p", Role.SELLER)));
        assertEquals(Role.ADMIN, UserFactory.toRole(authService.register("a", "p", Role.ADMIN)));
    }

    // ===== FAKE DAO =====
    static class FakeUserDao implements UserDao {
        private final Map<String, User> usersById = new HashMap<>();

        @Override
        public void save(User user) {
            usersById.put(user.getId(), user);
        }

        @Override
        public Optional<User> findById(String id) {
            return id == null ? Optional.empty() : Optional.ofNullable(usersById.get(id));
        }

        @Override
        public Optional<User> findByUsername(String username) {
            if (username == null) return Optional.empty();
            return usersById.values().stream()
                    .filter(u -> u.getUsername().equals(username))
                    .findFirst();
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(usersById.values());
        }

        @Override
        public long updateBalance(String userId, long newBalance) {
            User u = usersById.get(userId);
            if (u == null) throw new IllegalStateException("user not found: " + userId);
            u.setBalance(newBalance);
            return newBalance;
        }
    }
}