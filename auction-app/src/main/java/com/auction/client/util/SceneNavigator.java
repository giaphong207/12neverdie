package com.auction.client.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public final class SceneNavigator {

    private static Stage stage;
    private static Object currentController;   // ← THÊM: nhớ controller hiện tại

    private SceneNavigator() {
    }

    public static void setStage(Stage primaryStage) {
        stage = primaryStage;
    }

    public static void switchScene(String fxmlPath) {
        try {
            URL resource = SceneNavigator.class.getResource(fxmlPath);

            if (resource == null) {
                throw new IllegalArgumentException("Cannot find FXML file: " + fxmlPath);
            }

            // 1. Dọn controller cũ trước khi load scene mới
            disposeCurrentController();

            // 2. Load FXML bằng instance loader để lấy được controller
            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();
            currentController = loader.getController();   // ← THÊM: lưu lại

            // 3. Phần dưới giữ nguyên như cũ
            Scene scene = new Scene(root, 1280, 800);
            SceneStyler.apply(scene);

            stage.setScene(scene);
            stage.setMinWidth(1024);
            stage.setMinHeight(720);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load scene: " + fxmlPath, e);
        }
    }

    /**
     * Gọi dispose() của controller hiện tại nếu nó implement Disposable.
     * Bọc try-catch để controller dispose lỗi không làm crash app hoặc
     * block việc chuyển scene.
     */
    private static void disposeCurrentController() {
        if (currentController instanceof Disposable d) {
            try {
                d.dispose();
            } catch (Exception e) {
                System.err.println("Error disposing controller "
                        + currentController.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        currentController = null;
    }
}