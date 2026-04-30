package com.auction.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.auction.shared.model.Role;
import com.auction.shared.model.User;

public class AuthServiceTest {
    private AuthService authService;

    @BeforeEach
    public void setUp() {
        //Khởi tạo AuthService trước mỗi test
        authService = new DefaultAuthService();
    }

    //TEST CASE ĐĂNG NHẬP

    @Test
    public void shouldLoginSuccessWithValidCredentials() {
        //Arrange: Đăng ký user trước
        User registered = authService.register("testuser", "password123", Role.BIDDER); //check register thành công trước
        assertNotNull(registered, "Register phải thành công");

        //Act: Đăng nhập
        User user = authService.login("testuser", "password123");

        //Assert: Kiểm tra kết quả
        assertNotNull(user, "User không được null");
        assertEquals("testuser", user.getUsername());
        assertEquals(Role.BIDDER, user.getRole());
    }

    @Test
    public void shouldLoginFailWithWrongPassword() {
        //Arrange: Đăng ký user
        authService.register("testuser", "password123", Role.BIDDER);

        //Act: Đăng nhập với password sai
        User user = authService.login("testuser", "wrongpassword");

        //Assert: Phải return null
        assertNull(user, "Đăng nhập sai password phải return null");
    }

    @Test
    public void shouldLoginFailWithNonExistentUser() {
        //Act: Đăng nhập với user không tồn tại
        User user = authService.login("nonexistent", "password123");

        //Assert: Phải return null
        assertNull(user, "Đăng nhập user không tồn tại phải return null");
    }

    @Test
    public void shouldLoginFailWhenUsernameIsEmpty() {
        //Act: Đăng nhập với username rỗng
        User user = authService.login("", "password123");

        //Assert: Phải return null
        assertNull(user, "Đăng nhập username rỗng phải return null");
    }

    //TEST CASE ĐĂNG KÝ

    @Test
    public void shouldRegisterNewUserSuccessfully() {
        //Act: Đăng ký user mới
        User user = authService.register("newuser", "pass123", Role.SELLER);

        //Assert: Kiểm tra user được tạo
        assertNotNull(user, "User mới phải không null");
        assertEquals("newuser", user.getUsername());
        assertEquals("pass123", user.getPassword());
        assertEquals(Role.SELLER, user.getRole());
        assertNotNull(user.getId(), "User ID phải được sinh ra");
    }

    @Test
    public void shouldNotRegisterDuplicatedUsername() {
        //Arrange: Đăng ký user lần 1
        authService.register("testuser", "pass123", Role.BIDDER);

        //Act: Cố gắng đăng ký lại username trùng
        User user = authService.register("testuser", "pass456", Role.SELLER);

        //Assert: Phải return null
        assertNull(user, "Đăng ký username trùng phải return null");
    }

    @Test
    public void shouldRegisterWithDifferentRoles() {
        //Test đăng ký với vai trò BIDDER
        User bidder = authService.register("bidderuser", "pass123", Role.BIDDER);
        assertNotNull(bidder);
        assertEquals(Role.BIDDER, bidder.getRole());

        //Test đăng ký với vai trò SELLER
        User seller = authService.register("selleruser", "pass123", Role.SELLER);
        assertNotNull(seller);
        assertEquals(Role.SELLER, seller.getRole());
    }

    //TEST CASE KIỂM TRA USERNAME

    @Test
    public void shouldCheckUsernameExistsReturnTrue() {
        //Arrange: Đăng ký user
        authService.register("testuser", "pass123", Role.BIDDER);

        //Act & Assert: Kiểm tra username tồn tại
        assertTrue(authService.usernameExists("testuser"),
                "usernameExists phải return true khi user tồn tại");
    }

    @Test
    public void shouldCheckUsernameExistsReturnFalse() {
        //Act & Assert: Kiểm tra username không tồn tại
        assertFalse(authService.usernameExists("nonexistent"),
                "usernameExists phải return false khi user không tồn tại");
    }

    @Test
    public void shouldCheckEmptyUsernameReturnFalse() {
        //Act và Assert: Kiểm tra username rỗng
        assertFalse(authService.usernameExists(""),
                "usernameExists với username rỗng phải return false");
    }

    //TEST CASE ADMIN SEED 
    @Test
    public void shouldAdminAccountBeSeededOnStartup() {
        //Act: Kiểm tra admin account có tồn tại không
        boolean adminExists = authService.usernameExists("admin");

        //Assert: Admin phải được seed sẵn
        assertTrue(adminExists, "Admin account phải được seed sẵn");
    }

    @Test
    public void shouldLoginAsAdminWithDefaultPassword() {
        //Act: Đăng nhập với admin default
        User adminUser = authService.login("admin", "admin123");

        //Assert: Kiểm tra admin login thành công
        assertNotNull(adminUser, "Admin login phải thành công");
        assertEquals("admin", adminUser.getUsername());
        assertEquals(Role.ADMIN, adminUser.getRole());
    }
}
//Cấu trúc AAA (Arrange - Act - Assert): Chuẩn bị dữ liệu (Arrange) -> Gọi hàm cần test (Act) -> Kiểm tra kết quả (Assert)