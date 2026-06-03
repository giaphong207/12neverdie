# Hệ Thống Đấu Giá Trực Tuyến (Online Auction System)

*Bài tập lớn môn Lập trình nâng cao — Học kỳ II, 2025–2026 — Nhóm 12*

Hệ thống đấu giá trực tuyến theo kiến trúc **Client–Server**: nhiều người dùng (Bidder / Seller / Admin) kết nối tới một server trung tâm để đăng sản phẩm, mở phiên đấu giá và đặt giá theo thời gian thực. Khi có người đặt giá mới, mọi client đang theo dõi phiên được cập nhật ngay lập tức qua socket.

- **Mã nguồn:** https://github.com/giaphong207/12neverdie
- **Báo cáo PDF:** https://drive.google.com/drive/folders/1TwJt9onZAq7FgEnuWqFSdLnj1whwsgSg?dmr=1&ec=wgc-drive-%5Bmodule%5D-goto
- **Video demo:** (https://drive.google.com/drive/folders/1frkh2BYg4hwdlwvRBGoskYow2EbYJIm8?dmr=1&ec=wgc-drive-%5Bmodule%5D-goto)

---

## 1. Mô tả bài toán & Phạm vi hệ thống

**Bài toán:** Mô phỏng một sàn đấu giá trực tuyến nhiều người dùng. Người bán mở phiên cho sản phẩm; nhiều người mua cùng đặt giá theo thời gian thực; hệ thống phải bảo đảm tính đúng đắn khi nhiều người đặt giá *đồng thời*, tự đóng phiên đúng giờ và xác định người thắng.

**Phạm vi:**

- Quản lý người dùng & phân quyền (Admin / Seller / Bidder).
- Quản lý sản phẩm và phiên đấu giá (tạo, sửa, hủy phiên); Seller xem các phiên đã bán.
- Đặt giá theo thời gian thực, kiểm tra hợp lệ; ví tiền (nạp tiền & thanh toán tự động).
- Tự động đóng phiên đúng giờ & xác định người thắng.
- Tính năng nâng cao: đấu giá tự động (có bật/tắt), chống "cướp giá" phút chót, biểu đồ giá realtime.

> **Lưu trữ:** Dữ liệu được lưu trong **MySQL** (nhóm nâng cấp từ yêu cầu lưu File I/O ban đầu lên CSDL thật, để có transaction & truy vấn thống kê). Các đối tượng vẫn `Serializable` để truyền qua socket.
>
> **Ngoài phạm vi:** không tích hợp cổng thanh toán tiền thật (ví dùng số dư nội bộ); không triển khai production.

---

## 2. Công nghệ sử dụng, môi trường & yêu cầu cài đặt

| Hạng mục | Công nghệ |
|---|---|
| Ngôn ngữ | Java — JDK 25 |
| Build tool | Maven 3.9+ |
| Giao diện | JavaFX 21.0.4 (FXML) |
| Giao tiếp | Java Socket (TCP) — cổng **9999** |
| Cơ sở dữ liệu | MySQL 8.x + HikariCP (connection pool) — cổng **3306** |
| Bảo mật | BCrypt (hash mật khẩu) |
| Logging | SLF4J + slf4j-simple |
| Kiến trúc | MVC + DAO Pattern, 3 tầng (client / server / shared) |
| Kiểm thử | JUnit 5 + H2 (in-memory DB cho test) |
| Chất lượng & CI | JaCoCo (coverage), Checkstyle (Google style), GitHub Actions |

**Cần cài trước khi chạy:**

- **JDK 25** (bắt buộc — đồng bộ với cấu hình CI/CD).
- **Maven 3.9+** (hoặc dùng Maven tích hợp sẵn trong IntelliJ IDEA).
- **MySQL Server 8.x** đang chạy ở `localhost:3306`.

> Kiểm tra nhanh: `java -version` (phải là 25) và `mvn -version`.

---

## 3. Cấu trúc thư mục / Các module chính

```text
12neverdie/
├── README.md
├── .github/
│   └── workflows/
│       └── ci.yml                 # CI: tự động build + test khi push/PR
└── auction-app/
    ├── pom.xml                    # Cấu hình Maven, dependencies & plugin
    ├── sql/
    │   └── schema.sql             # Tạo 5 bảng: users, items, auctions, bids, auto_bid_configs
    └── src/
        ├── main/
        │   ├── java/com/auction/
        │   │   ├── client/        # JavaFX: controller, network, realtime, chart, context, util, main
        │   │   ├── server/        # Server: dao, service, handler, realtime, concurrency, seed, main
        │   │   └── shared/        # Dùng chung: model, factory, exception, networkMessage, config
        │   └── resources/         # fxml/, css/, images/, db.properties.example, simplelogger.properties
        └── test/java/com/auction/ # Unit / integration / E2E test (JUnit 5)
```

Ba module chính:

- **client** — Giao diện JavaFX (MVC) và kết nối socket tới server: danh sách phiên, màn hình đấu giá realtime, biểu đồ giá. Lớp khởi động: `client/main/ClientApp.java` (và `ClientLauncher.java` để chạy từ IDE).
- **server** — Xử lý nghiệp vụ, truy cập MySQL, điều phối request và broadcast sự kiện realtime. Lớp khởi động: `server/main/ServerApp.java`.
- **shared** — Code dùng chung giữa client & server: model (User, Item, Auction, Bid — `sealed`), các `record` request/response, factory, exception, config.

---

## 4. Cài đặt (làm 1 lần cho máy mới)

### Bước 1 — Tạo database & user MySQL

Đăng nhập MySQL bằng `root` (Workbench hoặc terminal) và chạy. **Đặt một mật khẩu thật và ghi nhớ** để dùng ở Bước 3:

```sql
CREATE DATABASE auction_db CHARACTER SET utf8mb4;
CREATE USER 'auction_user'@'localhost' IDENTIFIED BY 'YOUR_PASSWORD';
GRANT ALL PRIVILEGES ON auction_db.* TO 'auction_user'@'localhost';
FLUSH PRIVILEGES;
```

> Mật khẩu này phải **thống nhất trong cả nhóm** — hỏi trưởng nhóm.

### Bước 2 — Tạo bảng

Chạy từ thư mục `auction-app` (giống nhau trên Windows / macOS / Linux):

```bash
mysql -u auction_user -p auction_db < sql/schema.sql
```

Script tạo 5 bảng cho hệ thống. Máy mới chỉ cần chạy `schema.sql` là đủ.

### Bước 3 — Cấu hình kết nối

Sao chép file cấu hình mẫu:

```bash
# macOS / Linux / Git Bash
cp src/main/resources/db.properties.example src/main/resources/db.properties
```

```bat
:: Windows (CMD)
copy src\main\resources\db.properties.example src\main\resources\db.properties
```

Mở `db.properties` vừa tạo, sửa dòng `db.password=` thành **đúng mật khẩu** đã đặt ở Bước 1.

> **Lưu ý:** Lần chạy đầu tiên, nếu database rỗng, Server **tự seed** dữ liệu demo (4 user, 3 sản phẩm, 3 phiên) — không cần nhập tay.

---

## 5. Chạy chương trình (Server / Client theo thứ tự)

> **Thứ tự bắt buộc:** **MySQL → Server → Client.** Mở nhiều Client cùng lúc để demo cập nhật realtime.

### Cách A — IntelliJ IDEA (khuyên dùng, chạy được trên Windows / macOS / Linux)

1. `File → Open` → chọn thư mục `auction-app`; đợi Maven tải dependencies (1–3 phút lần đầu).
2. Đặt SDK là **Java 25** (`File → Project Structure → Project SDK`).
3. Chạy **`ServerApp`** (`server/main/ServerApp.java`) — đợi log báo server lắng nghe cổng 9999.
4. Chạy **`ClientLauncher`** (`client/main/ClientLauncher.java`) — cửa sổ đăng nhập JavaFX mở ra. Chạy lại nhiều lần = nhiều client.

### Cách B — Dòng lệnh Maven (giống nhau trên mọi hệ điều hành)

Mở terminal tại thư mục `auction-app`. Lệnh Maven dưới đây chạy y hệt trên Windows, macOS và Linux.

**1) Build toàn bộ + chạy test + coverage + checkstyle (đúng lệnh CI dùng):**

```bash
mvn -B clean verify
```

**2) Chạy CLIENT** (đã cấu hình sẵn plugin, chạy được ngay):

```bash
mvn javafx:run
```

**3) Chạy SERVER.** Có hai cách, chọn một:

- **Cách khuyến nghị — thêm plugin `exec` (sửa `pom.xml` một lần).** Thêm vào khối `<plugins>` của `auction-app/pom.xml`:

```xml
  
    org.codehaus.mojo
    exec-maven-plugin
    3.5.0
  
```

  Sau đó chạy (giống nhau trên mọi hệ điều hành):

```bash
  mvn -q compile exec:java -Dexec.mainClass="com.auction.server.main.ServerApp"
```

- **Cách không sửa pom — chạy bằng classpath.** Tạo classpath rồi gọi `java` trực tiếp:

```bash
  mvn -q clean compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
```

```bash
  # macOS / Linux
  java -cp "target/classes:$(cat target/cp.txt)" com.auction.server.main.ServerApp
```

```powershell
  # Windows (PowerShell)
  java -cp "target/classes;$(Get-Content target/cp.txt)" com.auction.server.main.ServerApp
```

### Kiểm tra Server đã chạy (cổng 9999)

```bash
# Windows
netstat -ano | findstr 9999

# macOS / Linux
lsof -i :9999        # hoặc:  netstat -an | grep 9999
```

Thấy trạng thái `LISTENING` / `LISTEN` là Server đã sẵn sàng.

### Tài khoản demo (Server tự tạo sẵn)

| Tài khoản | Mật khẩu | Vai trò |
|---|---|---|
| `admin` | `admin123` | Admin |
| `seller1` | `seller123` | Seller |
| `bidder1` | `bid123` | Bidder |
| `bidder2` | `bid123` | Bidder |

---

## 6. Danh sách chức năng đã hoàn thành

### 6.1. Chức năng theo vai trò (Core)

#### a) Quản trị viên (Admin)
- Đăng nhập với quyền quản trị.
- Xem toàn bộ người dùng trong hệ thống (vai trò, số dư).
- Xem toàn bộ sản phẩm của mọi người bán.
- Gỡ (xoá) sản phẩm vi phạm.
- Huỷ bất kỳ phiên đấu giá nào.
- Xem báo cáo thống kê toàn hệ thống: tổng số user / seller / bidder, tổng sản phẩm, tổng phiên theo trạng thái (đang chạy / đã mở / kết thúc / đã thanh toán / đã huỷ), tổng lượt đặt giá và tổng doanh thu.

#### b) Người bán (Seller)
- Đăng ký / đăng nhập với vai người bán.
- Thêm sản phẩm (chọn loại: điện tử / nghệ thuật / phương tiện) — hệ thống tự tạo phiên đấu giá kèm bước giá tối thiểu.
- Sửa thông tin sản phẩm của mình.
- Xoá sản phẩm của mình (bị chặn nếu sản phẩm đang nằm trong phiên).
- Xem danh sách sản phẩm & phiên đấu giá của mình.
- Xem các phiên đã bán (Sold Auctions).
- Huỷ phiên đấu giá của chính mình.

#### c) Người đấu giá (Bidder)
- Đăng ký / đăng nhập với vai người mua.
- Xem danh sách phiên đấu giá và lọc theo trạng thái.
- Xem chi tiết phiên: giá hiện tại, người đang giữ giá, lịch sử đặt giá, đồng hồ đếm ngược và biểu đồ giá realtime.
- Đặt giá, có kiểm tra hợp lệ: giá mới ≥ giá hiện tại + bước giá; không được tự đấu giá / không đặt khi đang giữ giá cao nhất; phải đủ số dư ví.
- Đặt và huỷ đấu giá tự động (auto-bid) theo mức trần & bước giá.
- Theo dõi các phiên đã tham gia.
- Nạp tiền vào ví, xem số dư; tự động trừ tiền khi thắng phiên.

#### d) Chức năng của hệ thống (kỹ thuật & tự động)
- Kiến trúc Client–Server qua TCP Socket (cổng 9999), truyền `record` `Serializable`.
- Tự động đóng phiên đúng giờ và xác định người thắng (máy trạng thái `OPEN → RUNNING → FINISHED → PAID/CANCELED`).
- Cập nhật realtime cho mọi client qua socket (Observer) khi có giá mới, phiên thay đổi hoặc được gia hạn.
- Xử lý đấu giá đồng thời: `ReentrantLock` theo từng phiên + transaction; cập nhật ví **nguyên tử** ở mức CSDL nên không bị lost-update.
- Thanh toán tự động khi phiên kết thúc (chuyển khoản winner → seller trong một transaction).
- Xử lý lỗi & ngoại lệ tập trung bằng hệ thống custom exception, trả thông báo thân thiện cho người dùng.
- Bảo mật: băm mật khẩu bằng BCrypt; phân quyền theo vai trò.
- Lưu trữ dữ liệu bằng MySQL + HikariCP (object vẫn `Serializable` để truyền qua socket).
- Giao diện JavaFX (FXML) theo mô hình MVC — 16 màn hình.
- Thiết kế OOP (kế thừa, đa hình, trừu tượng, đóng gói — `sealed class`) và Design Patterns: Singleton, Factory, Observer, DAO.
- Quản lý build & dependencies bằng Maven; Unit Test JUnit 5 (~184 test, gồm test mô phỏng đồng thời & end-to-end); CI/CD GitHub Actions kèm JaCoCo (coverage) và Checkstyle.

### 6.2. Nâng cao (Optional)

- **Auto-Bidding** — hệ thống tự đặt giá thay người dùng đến mức trần; xử lý cascade khi nhiều người cùng đặt auto-bid; có bật/tắt.
- **Anti-Sniping** — tự gia hạn phiên thêm 60 giây khi có người đặt giá trong 30 giây cuối.
- **Bid History Visualization** — biểu đồ biến động giá theo thời gian thực (LineChart).

---

## 7. Báo cáo & Demo

- **Báo cáo PDF:** https://drive.google.com/drive/folders/1TwJt9onZAq7FgEnuWqFSdLnj1whwsgSg?dmr=1&ec=wgc-drive-%5Bmodule%5D-goto
- **Video demo:** https://drive.google.com/drive/folders/1frkh2BYg4hwdlwvRBGoskYow2EbYJIm8?dmr=1&ec=wgc-drive-%5Bmodule%5D-goto
- **Mã nguồn:** https://github.com/giaphong207/12neverdie