package com.auction.client.util;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.Optional;

/**
 * Dialog cấu hình Auto-Bid. Tách thành factory để tránh đụng layout
 * AuctionDetail.fxml (TV2 đang chạm file đó cho LineChart).
 *
 * Cách dùng trong AuctionDetailController.onConfigureAutoBidClicked():
 *
 *     Optional<AutoBidFormResult> result = AutoBidDialogFactory.showDialog();
 *     result.ifPresent(r -> {
 *         ServerConnection.getInstance().send(new SetAutoBidRequest(
 *                 currentAuctionId,
 *                 ClientSession.getCurrentUser().getId(),
 *                 r.maxAmount,
 *                 r.increment
 *         ));
 *     });
 */
public final class AutoBidDialogFactory {

    private AutoBidDialogFactory() {}

    public static class AutoBidFormResult {
        public final long maxAmount;
        public final long increment;

        public AutoBidFormResult(long maxAmount, long increment) {
            this.maxAmount = maxAmount;
            this.increment = increment;
        }
    }

    public static Optional<AutoBidFormResult> showDialog() {
        Dialog<AutoBidFormResult> dialog = new Dialog<>();
        dialog.setTitle("Cấu hình Auto-Bid");
        dialog.setHeaderText("Hệ thống sẽ tự bid thay bạn đến giá trần.");

        ButtonType saveBtn = new ButtonType("Lưu", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 20));

        TextField maxAmountField = new TextField();
        maxAmountField.setPromptText("VD: 1000000");

        TextField incrementField = new TextField();
        incrementField.setPromptText("VD: 10000");

        grid.add(new Label("Giá trần (max):"), 0, 0);
        grid.add(maxAmountField, 1, 0);
        grid.add(new Label("Bước giá (increment):"), 0, 1);
        grid.add(incrementField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == saveBtn) {
                try {
                    long max = MoneyParser.parseBidAmount(maxAmountField.getText());
                    long inc = MoneyParser.parseBidAmount(incrementField.getText());
                    return new AutoBidFormResult(max, inc);
                } catch (Exception ex) {
                    AlertUtils.showError("Sai định dạng", ex.getMessage());
                    return null;
                }
            }
            return null;
        });

        return dialog.showAndWait();
    }
}