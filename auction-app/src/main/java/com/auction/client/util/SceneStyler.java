package com.auction.client.util;

import javafx.scene.Scene;

import java.net.URL;

/**
 * Utility áp dụng theme CSS chung cho mọi Scene trong ứng dụng.
 * Mỗi khi tạo Scene mới (Login, Register, navigateByRole...), phải gọi
 * SceneStyler.apply(scene) ngay sau đó để giữ thiết kế đồng nhất.
 */
public final class SceneStyler {

    private static final String THEME_CSS = "/css/auctionhub-theme.css";

    private SceneStyler() {}

    /**
     * Áp dụng theme Library Bronze cho Scene.
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