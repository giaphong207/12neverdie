package com.auction.client.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public final class SceneNavigator {

    private static Stage stage;

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

            Parent root = FXMLLoader.load(resource);
            Scene scene = new Scene(root, 1280, 800);

            // Apply Library Bronze theme CSS — dùng SceneStyler thống nhất
            SceneStyler.apply(scene);

            stage.setScene(scene);
            stage.setMinWidth(1024);
            stage.setMinHeight(720);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load scene: " + fxmlPath, e);
        }
    }
}