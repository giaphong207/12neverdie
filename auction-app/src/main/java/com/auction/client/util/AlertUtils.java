package com.auction.client.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;

public final class AlertUtils {
    //Không cho phép khởi tạo đối tượng bằng từ khóa new
    private AlertUtils() {}

    //Hiển thị hộp thoại báo lỗi (Icon dấu X đỏ)
    public static void showError(String title, String message) {
        runOnFxThread(() -> show(Alert.AlertType.ERROR, title, message));
    }

    //Hiển thị hộp thoại thông báo thành công (Icon chữ i xanh)
    public static void showInfo(String title, String message) {
        runOnFxThread(() -> show(Alert.AlertType.INFORMATION, title, message));
    }

    //Hiển thị hộp thoại cảnh báo (Icon tam giác vàng)
    public static void showWarning(String title, String message) {
        runOnFxThread(() -> show(Alert.AlertType.WARNING, title, message));
    }

    private static void show(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }

    private static void runOnFxThread(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }
}
