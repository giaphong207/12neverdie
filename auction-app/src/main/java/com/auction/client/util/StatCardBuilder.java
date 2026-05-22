package com.auction.client.util;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Tạo stat card đẹp cho dashboard:
 * ┌──────────────────────┐
 * │ LABEL UPPERCASE TINY │
 * │                      │
 * │ 1.250.000.000 ₫      │ <- số to font serif
 * │                      │
 * │ +12% so với tháng    │ <- subtext (optional)
 * └──────────────────────┘
 */
public final class StatCardBuilder {

    private StatCardBuilder() {}

    public static VBox build(String label, String value) {
        return build(label, value, null);
    }

    public static VBox build(String label, String value, String subtext) {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(24));

        Label labelNode = new Label(label.toUpperCase());
        labelNode.getStyleClass().add("label-tiny-uppercase");

        Label valueNode = new Label(value);
        valueNode.getStyleClass().add("title-serif-medium");

        card.getChildren().addAll(labelNode, valueNode);

        if (subtext != null && !subtext.isBlank()) {
            Label subNode = new Label(subtext);
            subNode.getStyleClass().add("text-tertiary");
            subNode.setStyle("-fx-font-size: 11px;");
            card.getChildren().add(subNode);
        }

        return card;
    }
}