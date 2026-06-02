package com.auction.client.util;

import java.util.Optional;   

import javafx.scene.control.Alert; 
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.stage.StageStyle; 

public final class AlertUtils {
    //Không cho phép khởi tạo đối tượng bằng từ khóa new
    private AlertUtils() {}

    // Hộp thoại báo lỗi (viền đỏ bordeaux)
    public static void showError(String title, String message) {
        styled(Alert.AlertType.ERROR, "app-dialog-error", "\u2715", title, message)
                .showAndWait();
    }

    // Hộp thoại thông báo thành công (viền xanh lá)
    public static void showInfo(String title, String message) {
        styled(Alert.AlertType.INFORMATION, "app-dialog-info", "\u2713", title, message)
                .showAndWait();
    }
    
    // Hộp thoại cảnh báo (viền vàng gold)
    public static void showWarning(String title, String message) {
        styled(Alert.AlertType.WARNING, "app-dialog-warning", "!", title, message)
                .showAndWait();
    }

    // Hộp thoại xác nhận (OK/Cancel) — trả về true nếu user bấm OK
    public static boolean showConfirm(String title, String message) {
        Alert alert = styled(Alert.AlertType.CONFIRMATION, "app-dialog-confirm", "?", title, message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Dựng 1 Alert đã được "khoác" theme app.
     *
     * @param type       loại Alert (quyết định nút mặc định OK / OK-Cancel)
     * @param styleClass class CSS phân loại màu (app-dialog-info / -error / ...)
     * @param glyph      ký tự icon vẽ trong vòng tròn (✓ ✕ ! ?)
     * @param title      tiêu đề (hiển thị dạng serif bên trong card)
     * @param message    nội dung
     */
    private static Alert styled(Alert.AlertType type, String styleClass,
                                String glyph, String title, String message) {
        Alert alert = new Alert(type);
 
        // (1) Bỏ thanh tiêu đề mặc định của Windows → card "nổi" trong suốt.
        //     Phải gọi TRƯỚC khi dialog hiện lần đầu.
        alert.initStyle(StageStyle.TRANSPARENT);
 
        alert.setTitle(title);          // tên cửa sổ (cho taskbar) — không hiển thị vì đã ẩn title bar
        alert.setHeaderText(title);     // tiêu đề serif hiển thị bên trong card
        alert.setContentText(message);
 
        // (2) Icon tròn tự vẽ thay cho icon mặc định của hệ điều hành.
        Label icon = new Label(glyph);
        icon.getStyleClass().add("dialog-icon");
        alert.setGraphic(icon);
 
        // (3) Gắn style-class + nạp app.css cho DialogPane.
        DialogPane pane = alert.getDialogPane();
        pane.getStyleClass().addAll("app-dialog", styleClass);
        SceneStyler.applyTo(pane);
 
        // (4) Để bo góc + đổ bóng hiển thị đúng, Scene của dialog phải trong suốt.
        //     Scene chỉ được tạo khi dialog sắp hiện, nên ta lắng nghe sceneProperty.
        pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.setFill(Color.TRANSPARENT);
            }
        });
 
        return alert;
    }
}