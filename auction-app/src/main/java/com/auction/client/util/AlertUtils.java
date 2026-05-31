package com.auction.client.util;

import java.util.Optional;   

import javafx.scene.control.Alert; 
import javafx.scene.control.ButtonType; 

public final class AlertUtils {
    //Không cho phép khởi tạo đối tượng bằng từ khóa new
    private AlertUtils() {}

    //Hiển thị hộp thoại báo lỗi (Icon dấu X đỏ)
    public static void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait(); //showAndWait() = chờ user click OK rồi mới tiếp tục
    }

    //Hiển thị hộp thoại thông báo thành công (Icon chữ i xanh)
    public static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    //Hiển thị hộp thoại cảnh báo (Icon tam giác vàng)
    public static void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    //Hộp thoại xác nhận (OK/Cancel) — trả về true nếu user bấm OK
    public static boolean showConfirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}