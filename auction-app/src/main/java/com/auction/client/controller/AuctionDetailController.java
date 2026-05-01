package com.auction.client.controller;

import com.auction.client.context.ClientSession;
import com.auction.client.util.AlertUtils;
import com.auction.client.util.CountdownUtil;
import com.auction.client.util.MoneyParser;
import com.auction.client.util.SceneNavigator;
import com.auction.server.dao.FileAuctionDao;
import com.auction.server.service.AuctionManager;
import com.auction.server.service.BidService;
import com.auction.server.service.DefaultBidService;
import com.auction.shared.exception.AppException;
import com.auction.shared.model.Auction;
import com.auction.shared.model.Bid;
import com.auction.shared.model.Role;
import com.auction.shared.model.User;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class AuctionDetailController {

    @FXML private Label itemNameLabel;
    @FXML private Label descriptionLabel;
    @FXML private Label sellerLabel;
    @FXML private Label currentPriceLabel;
    @FXML private Label statusLabel;
    @FXML private Label remainingTimeLabel;
    @FXML private Label messageLabel;
    @FXML private Button placeBidButton;

    // --- CÁC BIẾN MỚI CỦA TUẦN 3 ---
    @FXML private TextField bidAmountField;
    @FXML private Label highestBidderLabel;
    @FXML private ListView<String> bidHistoryListView;

    private Timeline countdownTimeline;
    private LocalDateTime currentEndTime;
    private Auction currentAuction;

    // Khởi tạo Service xử lý logic đặt giá
    private final BidService bidService = new DefaultBidService(new FileAuctionDao());

    public void initialize() {
        // Tuần 3: Lấy ID thật từ Session do màn hình List truyền sang
        String auctionId = ClientSession.getSelectedAuctionId();
        if (auctionId != null) {
            loadAuction(auctionId);
        } else {
            messageLabel.setText("Lỗi: Không tìm thấy phiên đấu giá.");
            placeBidButton.setDisable(true);
        }
    }

    public void loadAuction(String auctionId) {
        // Tuần 3: Lấy dữ liệu thật từ database thông qua Manager
        Optional<Auction> optAuction = AuctionManager.getInstance().findById(auctionId);

        if (optAuction.isEmpty()) {
            AlertUtils.showError("Lỗi", "Không tìm thấy phiên đấu giá này!");
            onBackClicked();
            return;
        }

        currentAuction = optAuction.get();
        messageLabel.setText(""); // Xóa dòng chữ mock tuần 2
        renderAuction(currentAuction);
        startCountdown(currentAuction.getEndTime());
    }

    // --- HÀM MỚI: Hiển thị dữ liệu lên giao diện ---
    private void renderAuction(Auction auction) {
        itemNameLabel.setText(auction.getItemId()); // Tạm in ID, Tuần sau TV2 sẽ map ra Tên sp
        descriptionLabel.setText("Thông tin chi tiết sản phẩm...");
        sellerLabel.setText(auction.getSellerId());
        currentPriceLabel.setText(String.format("%,d VNĐ", auction.getCurrentPrice()));
        statusLabel.setText(auction.getStatus().toString());

        String leader = auction.getHighestBidderId();
        highestBidderLabel.setText("Người dẫn đầu: " + (leader != null ? leader : "Chưa có"));

        // Render lịch sử đấu giá (đảo ngược để bid mới nhất lên đầu)
        if (bidHistoryListView != null) {
            bidHistoryListView.getItems().clear();
            List<Bid> bids = auction.getBids();
            if (bids.isEmpty()) {
                bidHistoryListView.getItems().add("Chưa có ai đặt giá.");
            } else {
                for (int i = bids.size() - 1; i >= 0; i--) {
                    Bid b = bids.get(i);
                    bidHistoryListView.getItems().add(
                            b.getBidderId() + " -> " + String.format("%,d VNĐ", b.getAmount())
                    );
                }
            }
        }
    }

    // --- CÁC HÀM CŨ CỦA BẠN (Đếm ngược thời gian) ---
    private void startCountdown(LocalDateTime endTime) {
        stopCountdown();
        currentEndTime = endTime;
        updateRemainingTime();

        countdownTimeline = new Timeline(
                new KeyFrame(javafx.util.Duration.seconds(1), event -> updateRemainingTime())
        );
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    private void updateRemainingTime() {
        if (currentEndTime == null) {
            remainingTimeLabel.setText("Không có dữ liệu thời gian");
            placeBidButton.setDisable(true);
            return;
        }

        java.time.Duration remaining = java.time.Duration.between(LocalDateTime.now(), currentEndTime);

        if (remaining.isZero() || remaining.isNegative()) {
            remainingTimeLabel.setText("Đã kết thúc");
            statusLabel.setText("FINISHED");
            placeBidButton.setDisable(true);
            messageLabel.setText("Phiên đấu giá đã kết thúc.");
            stopCountdown();
            return;
        }

        remainingTimeLabel.setText(CountdownUtil.formatRemaining(remaining));
        placeBidButton.setDisable(false);
    }

    private void stopCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
    }

    public void onBackClicked() {
        stopCountdown();
        SceneNavigator.switchScene("/fxml/AuctionList.fxml");
    }

    // --- HÀM MỚI: Xử lý khi bấm nút Place Bid ---
    public void onPlaceBidClicked() {
        try {
            // 1. Lấy dữ liệu và ép kiểu an toàn qua tiện ích của TV1
            String rawAmount = bidAmountField.getText();
            long amount = MoneyParser.parseBidAmount(rawAmount);

            // 2. Kiểm tra tài khoản đăng nhập qua Session
            User currentUser = ClientSession.getCurrentUser();
            if (currentUser == null) {
                AlertUtils.showWarning("Chưa đăng nhập", "Vui lòng đăng nhập để tham gia đấu giá.");
                return;
            }
            if (currentUser.getRole() != Role.BIDDER) {
                AlertUtils.showError("Lỗi Quyền", "Chỉ tài khoản BIDDER mới được đặt giá!");
                return;
            }

            // 3. Thực thi logic đặt giá ở Service
            Auction updatedAuction = bidService.placeBid(
                    currentAuction.getId(),
                    currentUser.getId(),
                    amount
            );

            // 4. Cập nhật giao diện sau khi bid thành công
            currentAuction = updatedAuction;
            renderAuction(updatedAuction);
            if (bidAmountField != null) {
                bidAmountField.clear(); // Xóa số tiền cũ trên ô nhập liệu
            }

            // 5. Hiển thị thông báo
            AlertUtils.showInfo("Thành công", "Bạn đã đặt giá thành công và đang là người dẫn đầu!");

        } catch (AppException ex) {
            // Bắt lỗi nghiệp vụ (nhập sai, giá thấp, phiên đóng...)
            AlertUtils.showError("Lỗi đặt giá", ex.getMessage());
        } catch (Exception ex) {
            // Bắt các lỗi hệ thống khác
            AlertUtils.showError("Lỗi hệ thống", "Đã xảy ra sự cố: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}