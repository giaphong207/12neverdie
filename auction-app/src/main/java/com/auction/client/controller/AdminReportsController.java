package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.Disposable;
import com.auction.client.util.MoneyFormatter;
import com.auction.client.util.NavRouter;
import com.auction.client.util.RequestExecutor;
import com.auction.client.util.SceneNavigator;
import com.auction.client.util.SidebarBuilder.NavKey;
import com.auction.client.util.StatCardBuilder;
import com.auction.client.util.TopbarBuilder;
import com.auction.shared.networkMessage.Requests.GetAdminStatsRequest;
import com.auction.shared.networkMessage.Results.AdminStats;
import com.auction.shared.networkMessage.Results.GetAdminStatsResult;

import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

public class AdminReportsController implements Disposable {

    @FXML private StackPane topbarContainer;
    @FXML private Label summaryLabel;
    @FXML private HBox statCardsRow1;
    @FXML private HBox statCardsRow2;
    @FXML private BarChart<String, Number> auctionStatusChart;

    @FXML
    public void initialize() {
        if (topbarContainer != null && ClientSession.getCurrentUser() != null) {
            var topbar = TopbarBuilder.build(
                    ClientSession.getCurrentUser(),
                    NavKey.ADMIN_REPORTS,
                    this::handleNavClick,
                    this::handleLogout
            );
            topbarContainer.getChildren().add(topbar);
        }
        loadStats();
    }

    private void loadStats() {
        RequestExecutor.send(
                new GetAdminStatsRequest(),
                response -> {
                    if (response instanceof GetAdminStatsResult result) {
                        switch (result) {
                            case GetAdminStatsResult.Success s -> render(s.stats());
                            case GetAdminStatsResult.Failure f ->
                                    AlertUtils.showError("Lỗi", "Không tải được báo cáo: " + f.reason());
                        }
                    }
                },
                error -> AlertUtils.showError("Lỗi mạng", "Không tải được báo cáo: " + error)
        );
    }

    private void render(AdminStats st) {
        if (summaryLabel != null) {
            summaryLabel.setText("Cập nhật lúc " + java.time.LocalTime.now().withNano(0));
        }

        //hàng thẻ 1: người dùng + sản phẩm
        if (statCardsRow1 != null) {
            statCardsRow1.getChildren().clear();
            addCard(statCardsRow1, "Tổng người dùng", String.valueOf(st.totalUsers()), "Tài khoản");
            addCard(statCardsRow1, "Người bán", String.valueOf(st.sellers()), "Seller");
            addCard(statCardsRow1, "Người đấu giá", String.valueOf(st.bidders()), "Bidder");
            addCard(statCardsRow1, "Sản phẩm", String.valueOf(st.totalItems()), "Trong hệ thống");
        }

        //hàng thẻ 2: phiên + bid + doanh thu
        if (statCardsRow2 != null) {
            statCardsRow2.getChildren().clear();
            addCard(statCardsRow2, "Tổng phiên", String.valueOf(st.totalAuctions()), "Tất cả");
            addCard(statCardsRow2, "Đang diễn ra", String.valueOf(st.running()), "Running");
            addCard(statCardsRow2, "Tổng lượt đặt giá", String.valueOf(st.totalBids()), "Lượt");
            addCard(statCardsRow2, "Doanh thu", MoneyFormatter.formatVnd(st.totalRevenue()), "Đã thanh toán");
        }

        //biểu đồ cột: phiên theo trạng thái
        if (auctionStatusChart != null) {
            auctionStatusChart.getData().clear();
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.getData().add(new XYChart.Data<>("Sắp diễn ra", st.open()));
            series.getData().add(new XYChart.Data<>("Đang diễn ra", st.running()));
            series.getData().add(new XYChart.Data<>("Đã kết thúc", st.finished()));
            series.getData().add(new XYChart.Data<>("Đã thanh toán", st.paid()));
            series.getData().add(new XYChart.Data<>("Đã hủy", st.canceled()));
            auctionStatusChart.getData().add(series);
        }
    }

    private void addCard(HBox row, String label, String value, String subtext) {
        var card = StatCardBuilder.build(label, value, subtext);
        HBox.setHgrow(card, Priority.ALWAYS);
        row.getChildren().add(card);
    }

    private void handleNavClick(NavKey key) {
        if (key == NavKey.ADMIN_REPORTS) return; // đang ở đây
        NavRouter.route(key);
    }

    private void handleLogout() {
        ClientSession.clear();
        SceneNavigator.switchScene("/fxml/Login.fxml");
    }

    @Override
    public void dispose() {
        //ko subscribe event nào nên ko cần dọn dẹp.
    }
}