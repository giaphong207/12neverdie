# Hệ Thống Đấu Giá Trực Tuyến (Online Auction System)

*Bài tập lớn môn Lập trình nâng cao — Học kỳ II, 2025–2026 — Nhóm 12*

Hệ thống đấu giá trực tuyến theo kiến trúc **Client–Server**: nhiều người dùng (Bidder / Seller / Admin) kết nối tới một server trung tâm để đăng sản phẩm, mở phiên đấu giá và đặt giá theo thời gian thực. Khi có người đặt giá mới, mọi client đang theo dõi phiên được cập nhật ngay lập tức qua socket.

---

## 1. Giới thiệu & Phạm vi

Phạm vi hệ thống:

- Quản lý người dùng & phân quyền (Admin / Seller / Bidder).
- Quản lý sản phẩm và phiên đấu giá (tạo, sửa, hủy phiên).
- Đặt giá theo thời gian thực, kiểm tra hợp lệ, ví tiền (nạp tiền & thanh toán).
- Tự động đóng phiên đúng giờ & xác định người thắng.
- Tính năng nâng cao: đấu giá tự động, chống "cướp giá" phút chót, biểu đồ giá realtime.

> **Lưu trữ:** Dữ liệu được lưu trong **MySQL** (nhóm nâng cấp từ yêu cầu lưu File I/O ban đầu lên CSDL thật). Các đối tượng vẫn `Serializable` để truyền qua socket.

---

## 2. Công nghệ & Yêu cầu môi trường

| Hạng mục | Công nghệ |
|---|---|
| Ngôn ngữ | Java — JDK 25 |
| Build tool | Maven 3.9+ |
| Giao diện | JavaFX 21.0.4 (FXML) |
| Giao tiếp | Java Socket (TCP) — cổng 9999 |
| Cơ sở dữ liệu | MySQL 8.x + HikariCP (connection pool) |
| Bảo mật | BCrypt (hash mật khẩu) |
| Logging | SLF4J (slf4j-simple) |
| Kiến trúc | MVC + DAO Pattern, 3 tầng (client / server / shared) |
| Kiểm thử & CI | JUnit 5, JaCoCo (coverage), Checkstyle, GitHub Actions |

Cần cài trước khi chạy:

- **JDK 25** (bắt buộc, để đồng bộ với CI/CD)
- **Maven 3.9+** (hoặc dùng Maven tích hợp sẵn trong IntelliJ)
- **MySQL Server 8.x**

---

## 3. Cấu trúc thư mục

```text
12neverdie/
├── README.md
├── .github/
│   └── workflows/
│       └── ci.yml                 # CI: tự động build + test khi push/PR
└── auction-app/
    ├── pom.xml                    # Cấu hình Maven & dependencies
    ├── sql/
    │   └── schema.sql             # Tạo 5 bảng: users, items, auctions, bids, auto_bid_configs
    └── src/
        ├── main/
        │   ├── java/com/auction/
        │   │   ├── client/        # JavaFX: controller, network, realtime, chart, context, util, main
        │   │   ├── server/        # Server: dao, service, handler, realtime, concurrency, seed, main
        │   │   └── shared/        # Dùng chung: model, factory, exception, networkMessage, config
        │   └── resources/         # fxml/, css/, images/, db.properties.example, simplelogger.properties
        └── test/java/com/auction/ # Unit test & integration test (JUnit 5)
```

Ba module chính:

- **client** — Giao diện JavaFX và kết nối socket tới server. Hiển thị danh sách phiên, màn hình đấu giá realtime, biểu đồ giá.
- **server** — Xử lý nghiệp vụ, truy cập MySQL, điều phối request từ client và broadcast sự kiện realtime.
- **shared** — Code dùng chung giữa client & server: các model (User, Item, Auction, Bid), các `record` request/response, factory, exception, config.

---

## 4. Cài đặt (làm 1 lần cho máy mới)

### Bước 1 — Tạo database & user MySQL

Mở MySQL Workbench (login bằng `root`) và chạy:

```sql
CREATE DATABASE auction_db CHARACTER SET utf8mb4;
CREATE USER 'auction_user'@'localhost' IDENTIFIED BY '';
GRANT ALL PRIVILEGES ON auction_db.* TO 'auction_user'@'localhost';
FLUSH PRIVILEGES;
```

> **Lưu ý:** Mật khẩu phải thống nhất trong cả nhóm — hỏi trưởng nhóm.

### Bước 2 — Tạo bảng

```bash
mysql -u auction_user -p auction_db < auction-app/sql/schema.sql
```

Script tạo 5 bảng cho hệ thống đấu giá. Máy mới chỉ cần chạy `schema.sql` là đủ.

### Bước 3 — Cấu hình kết nối

```bash
# Linux / macOS / Git Bash
cp auction-app/src/main/resources/db.properties.example auction-app/src/main/resources/db.properties
```

Trên Windows (CMD) dùng `copy` thay cho `cp`. Sau đó mở `db.properties` vừa tạo, sửa `db.password=...` thành mật khẩu thật.

> **Mẹo:** Server tự seed dữ liệu demo (4 user, 3 sản phẩm, 3 phiên đấu giá) ở lần chạy đầu tiên nếu database đang rỗng — không cần nhập tay.

---

## 5. Chạy chương trình

> **Thứ tự bắt buộc:** MySQL → Server → Client. Có thể mở nhiều Client cùng lúc để demo cập nhật realtime.

### Cách A — Dòng lệnh (Windows / macOS / Linux)

Maven chạy giống nhau trên mọi hệ điều hành. Mở terminal tại thư mục `auction-app`:

```bash
cd auction-app
```

1. Build toàn bộ + chạy test (đúng lệnh CI dùng):

```bash
mvn -B clean verify
```

2. Chạy SERVER — ở terminal thứ 1, để cửa sổ này chạy:

```bash
mvn exec:java -Dexec.mainClass="com.auction.server.main.ServerApp"
```

3. Chạy CLIENT — ở terminal thứ 2 (mở thêm terminal nữa = thêm client):

```bash
mvn javafx:run
```

> **Lưu ý:** Nếu lệnh chạy server báo không tìm thấy plugin `exec`, hãy chạy `ServerApp` từ IntelliJ (Cách B), hoặc thêm `exec-maven-plugin` vào `pom.xml`.

### Cách B — IntelliJ IDEA

1. `File → Open` → chọn thư mục `auction-app`, đợi Maven tải dependencies (1–3 phút lần đầu).
2. Đảm bảo SDK là Java 25 (`File → Project Structure → Project SDK`).
3. Chạy `ServerApp` (`server/main/ServerApp.java`) — đợi console báo server đã sẵn sàng (cổng 9999).
4. Chạy `ClientLauncher` (`client/main/ClientLauncher.java`) — cửa sổ đăng nhập JavaFX mở ra.

> **Kiểm tra server đã chạy:**
> - Windows: `netstat -ano | findstr 9999`
> - macOS / Linux: `lsof -i :9999` (hoặc `netstat -an | grep 9999`)
>
> Thấy dòng `LISTENING` là OK.

### Tài khoản demo (server tự tạo sẵn)

| Tài khoản | Mật khẩu | Vai trò |
|---|---|---|
| `admin` | `admin123` | Admin |
| `seller1` | `seller123` | Seller |
| `bidder1` | `bid123` | Bidder |
| `bidder2` | `bid123` | Bidder |

---

## 6. Chức năng đã hoàn thành

### Bắt buộc (Core)

- Quản lý người dùng Admin / Seller / Bidder + đăng nhập / đăng ký / phân quyền.
- Quản lý sản phẩm: Seller thêm / sửa sản phẩm (kèm tạo phiên đấu giá); Admin gỡ sản phẩm vi phạm.
- Quản lý phiên đấu giá + hủy phiên (Seller hủy phiên của mình, Admin hủy mọi phiên).
- Đặt giá + kiểm tra hợp lệ (giá mới ≥ giá hiện tại + bước giá) + kiểm tra số dư ví.
- Tự động đóng phiên đúng giờ & xác định người thắng (`OPEN → RUNNING → FINISHED → PAID/CANCELED`).
- Xử lý lỗi & ngoại lệ (hệ thống custom exception riêng).
- Giao diện JavaFX (FXML) theo mô hình MVC.
- Thiết kế OOP: kế thừa, đa hình, trừu tượng, đóng gói (dùng `sealed class`).
- Design Patterns: **Singleton** (Database), **Factory** (User/Item), **Observer** (EventBus / broadcast), **DAO**.
- Kiến trúc Client–Server (TCP Socket, cổng 9999).
- Cập nhật realtime cho mọi client qua socket khi có giá mới.
- Xử lý đấu giá đồng thời (`ReentrantLock` theo từng phiên + transaction chống lost-update).
- Quản lý dependencies & build bằng Maven.
- Unit Test (JUnit 5) — 169 test.
- CI/CD (GitHub Actions — tự động build & test khi push/PR; tích hợp Checkstyle + JaCoCo coverage).
- Lưu trữ dữ liệu bằng MySQL (nâng cấp từ File I/O; object vẫn `Serializable` cho socket).

### Nâng cao (Optional)

- **Auto-Bidding** — hệ thống tự đặt giá thay người dùng theo mức trần & bước giá.
- **Anti-Sniping** — tự gia hạn phiên khi có người đặt giá vào những giây cuối.
- **Bid History Visualization** — biểu đồ biến động giá theo thời gian thực (LineChart).

### Tính năng thêm

- Ví tiền: nạp tiền (deposit), tự trừ / cộng số dư khi phiên thanh toán.

---

## 7. Báo cáo & Demo

- **Báo cáo PDF:** *(cập nhật link tại đây)*
- **Video demo:** *(cập nhật link tại đây)*
- **Mã nguồn:** https://github.com/giaphong207/12neverdie