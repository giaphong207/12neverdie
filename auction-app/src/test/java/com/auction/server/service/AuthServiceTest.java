package com.auction.server.service;

import com.auction.server.dao.UserDao;
import com.auction.shared.exception.AppExceptions.AuthenticationException;
import com.auction.shared.exception.AppExceptions.DuplicateUsernameException;
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
    @DisplayName("Register username trùng ném DuplicateUsernameException")
    void register_duplicate_throws() {
        authService.register("bob", "pwd", Role.BIDDER);
        assertThrows(DuplicateUsernameException.class,
                () -> authService.register("bob", "other", Role.SELLER));
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
    @DisplayName("Login sai password ném AuthenticationException")
    void login_wrong_password_throws() {
        authService.register("dave", "right", Role.BIDDER);
        assertThrows(AuthenticationException.class,
                () -> authService.login("dave", "wrong"));
    }

    @Test
    @DisplayName("Login user không tồn tại ném AuthenticationException")
    void login_unknown_user_throws() {
        assertThrows(AuthenticationException.class,
                () -> authService.login("ghost", "anything"));
    }

    @Test
    @DisplayName("Login với input null/empty ném AuthenticationException")
    void login_null_inputs() {
        assertThrows(AuthenticationException.class, () -> authService.login(null, "pwd"));
        assertThrows(AuthenticationException.class, () -> authService.login("user", null));
        assertThrows(AuthenticationException.class, () -> authService.login(null, null));
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