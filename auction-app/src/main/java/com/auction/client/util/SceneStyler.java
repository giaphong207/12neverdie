package com.auction.client.util;

import java.net.URL;

import javafx.scene.Scene;

/**
 * Utility áp dụng theme CSS chung cho mọi Scene trong ứng dụng.
 * Mỗi khi tạo Scene mới (Login, Register, navigateByRole...), phải gọi
 * SceneStyler.apply(scene) ngay sau đó để giữ thiết kế đồng nhất.
 */
public final class SceneStyler {

    private static final String THEME_CSS = "/css/app.css";

    private SceneStyler() {}

    /**
     * Áp dụng theme app.css cho Scene.
     * Theme đơn giản, trung tính, phù hợp dự án sinh viên.
     * Nếu Scene đã có stylesheet này thì bỏ qua (không thêm trùng).
     */
    public static void apply(Scene scene) {
        if (scene == null) {
            return;
        }

        URL cssUrl = SceneStyler.class.getResource(THEME_CSS);
        if (cssUrl == null) {
            System.err.println("[SceneStyler] KHÔNG tìm thấy file CSS: " + THEME_CSS);
            return;
        }

        String cssPath = cssUrl.toExternalForm();
        if (!scene.getStylesheets().contains(cssPath)) {
            scene.getStylesheets().add(cssPath);
        }
    }
}