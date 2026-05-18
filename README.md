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

## Setup máy mới

1. Cài JDK 25, IntelliJ, MySQL 8.4
2. Tạo DB và user:
```sql
   CREATE DATABASE auction_db CHARACTER SET utf8mb4;
   CREATE USER 'auction_user'@'localhost' IDENTIFIED BY 'password';
   GRANT ALL PRIVILEGES ON auction_db.* TO 'auction_user'@'localhost';
   FLUSH PRIVILEGES;
```
3. Tạo bảng:
```bash
   mysql -u auction_user -p auction_db < sql/schema.sql
```
4. (Optional) Seed data demo:
```bash
   mysql -u auction_user -p auction_db < sql/seed.sql
```
5. Copy `db.properties.example` thành `db.properties`, sửa password
6. Mở project trong IntelliJ → Maven auto-import → chạy `ServerApp`

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
   
## Tài khoản mặc định
| Tài khoản | Mật khẩu | Role   |
|-----------|----------|--------|
| admin     | admin123 | Admin  |
| seller1   | 123456   | Seller |
| bidder1   | 123456   | Bidder |
| bidder2   | 123456   | Bidder |
| bidder3   | 123456   | Bidder |

## Reset data trước khi demo anti-sniping
Xóa file `data/database.dat` rồi restart server.


## Thành viên 3 thêm vào đoạn này, xem và mix nhé:
# Hệ thống Đấu giá Trực tuyến

Bài tập lớn môn LTNC 2026 — Online Auction System.

## Kiến trúc

- **Client:** JavaFX desktop app
- **Server:** Java TCP Socket (port 9999)
- **Database:** MySQL 8.4 + HikariCP connection pool

## Yêu cầu môi trường

- JDK 25
- Maven 3.6+
- MySQL Server 8.4
- IntelliJ IDEA (khuyến nghị)

## Setup máy mới (1 lần)

### Bước 1: Cài đặt MySQL

Tải MySQL Server 8.4 từ https://dev.mysql.com/downloads/installer/

Sau khi cài xong, mở MySQL Workbench, login bằng root.

### Bước 2: Tạo database và user

Trong Workbench, chạy SQL sau (đổi `<password>` thành mật khẩu của bạn):

```sql
CREATE DATABASE auction_db CHARACTER SET utf8mb4;
CREATE USER 'auction_user'@'localhost' IDENTIFIED BY '<password>';
GRANT ALL PRIVILEGES ON auction_db.* TO 'auction_user'@'localhost';
FLUSH PRIVILEGES;
```

> ⚠️ Password phải thống nhất với cả nhóm. Hỏi trưởng nhóm để biết.

### Bước 3: Tạo bảng

Tải code về:
```bash
git clone <repo-url>
cd auction-app
```

Chạy script schema:
```bash
mysql -u auction_user -p auction_db < sql/schema.sql
```

(Hoặc mở `sql/schema.sql` trong Workbench → bôi đen tất cả → Ctrl+Shift+Enter)

### Bước 4: Cấu hình kết nối

Copy file template:
```bash
cp src/main/resources/db.properties.example src/main/resources/db.properties
```

Mở file `db.properties` vừa copy, sửa `db.password=...` thành password thật.

### Bước 5: Mở project trong IntelliJ

1. File → Open → chọn folder `auction-app`
2. Đợi Maven tự tải dependencies (1-3 phút lần đầu)
3. Build → Build Project (Ctrl+F9)

### Bước 6: Chạy

1. **Run ServerApp** (`src/main/java/com/auction/server/main/ServerApp.java`)
    - Đợi log `Server đang lắng nghe tại port: 9999`
    - Server tự seed 4 user demo: admin, seller1, bidder1, bidder2

2. **Run ClientLauncher** (`src/main/java/com/auction/client/main/ClientLauncher.java`)
    - Cửa sổ JavaFX Login mở

3. **Tài khoản demo:**
    - `admin` / `admin123`
    - `seller1` / `seller123`
    - `bidder1` / `bid123`
    - `bidder2` / `bid123`

## Cấu trúc project