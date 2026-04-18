package com.auction.client.main;

import com.auction.client.util.SceneNavigator;
import javafx.application.Application;
import javafx.stage.Stage;

public class ClientApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        SceneNavigator.setStage(primaryStage);
        primaryStage.setTitle("Online Auction System");
        SceneNavigator.switchScene("/fxml/MainLayout.fxml");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}