# Hệ Thống Đấu Giá Trực Tuyến (Online Auction System)
Bài tập lớn môn Lập trình nâng cao - Nhóm 12

## Yêu cầu hệ thống
* **Java Version:** JDK 25 (Bắt buộc để đồng bộ với CI/CD)
* **Build Tool:** Maven 3.9.14
* **IDE:** - VS Code: Cài bộ "Extension Pack for Java" của Microsoft/Red Hat.
        ** - IntelliJ: Nhớ vào *Project Structure* chỉnh SDK về Java 25.
* **Giao tiếp:** Java Socket (TCP)
* **Kiến trúc:** Model-View-Controller (MVC) & DAO Pattern
* **CI/CD:** GitHub Actions (Tự động build & test)

## Tính năng chính

### Chức năng bắt buộc (Core)
- [x] **Quản lý thực thể:** User (Admin/Seller/Bidder), Item, Auction, Bid.
- [x] **Xác thực:** Đăng nhập, đăng ký và phân quyền người dùng.
- [x] **Đấu giá cơ bản:** Đặt giá, kiểm tra giá hợp lệ (giá mới > giá cũ + bước giá).
- [ ] **Lưu trữ:** Persistence dữ liệu qua File I/O (`.dat`).

### Chức năng nâng cao (Advanced)
- [ ] **Auto-Bidding (TV4):** Hệ thống tự động đặt giá thay người dùng dựa trên mức giá trần.
- [ ] **Anti-Sniping (TV3):** Tự động gia hạn thời gian phiên đấu giá nếu có người bid ở những giây cuối.
- [ ] **Real-time Price Curve (TV2):** Biểu đồ biến động giá trực quan bằng LineChart.
- [x] **Socket Real-time:** Cập nhật thông tin phiên đấu giá cho toàn bộ Client ngay lập tức khi có giá mới.

## Cách chạy ứng dụng
1. Di chuyển vào thư mục dự án:
   ```bash
   cd online-auction-system/auction-app
   