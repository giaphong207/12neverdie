package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.util.NavRouter;
import com.auction.client.util.SceneNavigator;
import com.auction.client.util.SidebarBuilder.NavKey;
import com.auction.client.util.TopbarBuilder;
import com.auction.shared.factory.UserFactory;
import com.auction.shared.model.user.User;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Trang "Hướng dẫn" — hiển thị ngay trong cửa sổ như mọi trang khác.
 * Nội dung khác nhau theo vai trò: Bidder (cách đấu giá), Seller (bán hàng),
 * Admin (quản trị). Dùng chung điều hướng nav cho mọi vai trò.
 */
public class HelpController {

    @FXML private StackPane topbarContainer;
    @FXML private Label pageTitle;
    @FXML private VBox sectionsBox;

    @FXML
    public void initialize() {
        User user = ClientSession.getCurrentUser();
        if (topbarContainer != null && user != null) {
            var topbar = TopbarBuilder.build(
                    user,
                    TopbarBuilder.helpKeyFor(user),   // highlight đúng mục "Hướng dẫn"
                    this::handleNavClick,
                    this::handleLogout
            );
            topbarContainer.getChildren().add(topbar);
        }

        renderSectionsFor(user);
    }

    /** Dựng nội dung hướng dẫn theo vai trò người dùng. */
    private void renderSectionsFor(User user) {
        if (sectionsBox == null) return;
        sectionsBox.getChildren().clear();

        if (user == null) {
            addBidderGuide();
            return;
        }
        switch (UserFactory.toRole(user)) {
            case SELLER -> addSellerGuide();
            case ADMIN -> addAdminGuide();
            default -> addBidderGuide();
        }
    }

    // ─────────────────────────── BIDDER ───────────────────────────
    private void addBidderGuide() {
        if (pageTitle != null) pageTitle.setText("Hướng dẫn cho người đấu giá");
        addSection("Về hệ thống",
                "Phiên Đấu Giá là nền tảng đấu giá trực tuyến thời gian thực của nhóm 12neverdie. "
                + "Mọi thay đổi giá được cập nhật ngay lập tức cho tất cả người đang xem cùng một phiên.");
        addSection("Các bước đấu giá",
                "1. Nạp tiền vào ví ở góc phải màn hình.\n"
                + "2. Vào \"Phiên đang diễn ra\" và chọn một phiên bạn quan tâm.\n"
                + "3. Nhập số tiền cao hơn giá hiện tại rồi bấm \"Đặt giá\".\n"
                + "4. (Tuỳ chọn) Bật \"Đấu giá tự động\" để hệ thống tự nâng giá đến mức tối đa bạn đặt.\n"
                + "5. Khi phiên kết thúc, người trả giá cao nhất thắng và tiền được trừ tự động.");
        addSection("Quy tắc",
                "• Mỗi lần đặt giá phải cao hơn giá hiện tại ít nhất một bước giá.\n"
                + "• Số dư ví phải đủ cho mức giá bạn đặt (hoặc mức tối đa khi auto-bid).\n"
                + "• Nếu có người đặt giá vào phút chót, phiên sẽ được gia hạn thêm để công bằng.\n"
                + "• Khi đã thắng, giao dịch được thanh toán tự động và không thể huỷ.\n"
                + "• Hành vi gian lận hoặc phá giá có thể bị khoá tài khoản.");
        addContact();
    }

    // ─────────────────────────── SELLER ───────────────────────────
    private void addSellerGuide() {
        if (pageTitle != null) pageTitle.setText("Hướng dẫn cho người bán");
        addSection("Về trang người bán",
                "Trang người bán giúp bạn đăng sản phẩm, mở phiên đấu giá và theo dõi kết quả. "
                + "Mục \"Tổng quan\" hiển thị thống kê nhanh; mục \"Phiên đấu giá\" liệt kê chi tiết "
                + "từng phiên kèm doanh thu.");
        addSection("Các bước bán hàng",
                "1. Vào \"Sản phẩm của tôi\" và bấm thêm sản phẩm mới.\n"
                + "2. Nhập tên, mô tả, giá khởi điểm và chọn thời gian bắt đầu / kết thúc phiên.\n"
                + "3. Lưu lại — hệ thống tự tạo phiên đấu giá theo thời gian bạn chọn.\n"
                + "4. Theo dõi lượt đặt giá realtime trong \"Phiên đấu giá\".\n"
                + "5. Khi phiên kết thúc và người thắng thanh toán, tiền tự động được cộng vào ví của bạn.");
        addSection("Quy tắc & lưu ý",
                "• Giá khởi điểm phải lớn hơn 0; bước giá tối thiểu do hệ thống tính theo giá khởi điểm.\n"
                + "• Thời gian bắt đầu không được nằm trong quá khứ.\n"
                + "• Không thể xoá sản phẩm đang gắn với một phiên đấu giá.\n"
                + "• Doanh thu được tính từ các phiên đã kết thúc / đã thanh toán.\n"
                + "• Phiên có thể được gia hạn nếu có người đặt giá vào phút chót.");
        addContact();
    }

    // ─────────────────────────── ADMIN ───────────────────────────
    private void addAdminGuide() {
        if (pageTitle != null) pageTitle.setText("Hướng dẫn cho quản trị viên");
        addSection("Về trang quản trị",
                "Trang quản trị cho phép bạn giám sát toàn bộ hệ thống: danh sách người dùng, "
                + "các phiên đấu giá và số liệu tổng quan của nền tảng.");
        addSection("Công việc chính",
                "1. \"Tổng quan\" — xem nhanh số người dùng, người bán, người mua.\n"
                + "2. \"Người dùng\" — tra cứu tài khoản và vai trò trong hệ thống.\n"
                + "3. \"Phiên đấu giá\" — theo dõi toàn bộ phiên đang/đã diễn ra.\n"
                + "4. \"Báo cáo\" — xem thống kê hoạt động của nền tảng.");
        addSection("Lưu ý",
                "• Thao tác quản trị ảnh hưởng tới nhiều người dùng — hãy kiểm tra kỹ trước khi thực hiện.\n"
                + "• Tài khoản quản trị không tham gia đấu giá nên không có ví.\n"
                + "• Khi phát hiện gian lận, ưu tiên xác minh trước khi khoá tài khoản.");
        addContact();
    }

    private void addContact() {
        addSection("Liên hệ hỗ trợ",
                "Hotline: 1900 1212 (8:00 – 21:00 hằng ngày)\n"
                + "Email: support@12neverdie.vn");
    }

    /** Một mục hướng dẫn: tiêu đề + nội dung. */
    private void addSection(String title, String body) {
        VBox box = new VBox(8);
        Label t = new Label(title);
        t.getStyleClass().add("help-title");
        Label b = new Label(body);
        b.getStyleClass().add("help-text");
        b.setWrapText(true);
        box.getChildren().addAll(t, b);
        sectionsBox.getChildren().add(box);
    }

    private void handleNavClick(NavKey key) {
        // "Hướng dẫn" (các *_SETTINGS) → đang ở trang này, không làm gì
        if (key == TopbarBuilder.helpKeyFor(ClientSession.getCurrentUser())) return;
        NavRouter.route(key);
    }

    private void handleLogout() {
        ClientSession.clear();
        SceneNavigator.switchScene("/fxml/Login.fxml");
    }
}
