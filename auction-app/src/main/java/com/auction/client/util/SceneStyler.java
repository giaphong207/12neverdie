package com.auction.client.util;

import java.net.URL;

import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

/**
 * Utility áp dụng theme CSS chung cho mọi Scene / DialogPane trong ứng dụng.
 * Mỗi khi tạo Scene mới (Login, Register, navigateByRole...), gọi
 * SceneStyler.apply(scene). Với Alert/hộp thoại thì gọi SceneStyler.applyTo(pane).
 */
public final class SceneStyler {
 
    private static final String THEME_CSS = "/css/app.css";
 
    private SceneStyler() {}
 
    /** Áp dụng theme app.css cho Scene của màn hình chính. */
    public static void apply(Scene scene) {
        if (scene == null) {
            return;
        }
        addTheme(scene.getStylesheets());
    }
 
    /**
     * Áp dụng theme app.css cho DialogPane (Alert, hộp thoại...).
     * Alert có Scene RIÊNG nên app.css phải nạp THẲNG vào DialogPane thì
     * các style-class (.app-dialog, .dialog-icon...) mới có hiệu lực.
     */
    public static void applyTo(DialogPane pane) {
        if (pane == null) {
            return;
        }
        addTheme(pane.getStylesheets());
    }
 
    /**
     * Nơi DUY NHẤT chứa logic nạp theme: tìm file app.css rồi thêm vào danh sách
     * stylesheet (nếu chưa có). Cả Scene lẫn DialogPane đều trả về cùng kiểu
     * ObservableList&lt;String&gt; nên dùng chung được — tránh lặp code (DRY).
     */
    private static void addTheme(ObservableList<String> stylesheets) {
        if (stylesheets == null) {
            return;
        }
 
        URL cssUrl = SceneStyler.class.getResource(THEME_CSS);
        if (cssUrl == null) {
            System.err.println("[SceneStyler] KHÔNG tìm thấy file CSS: " + THEME_CSS);
            return;
        }
 
        String cssPath = cssUrl.toExternalForm();
        if (!stylesheets.contains(cssPath)) {
            stylesheets.add(cssPath);
        }
    }
}