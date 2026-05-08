package com.auction.client.chart;

import com.auction.shared.model.Bid;
import javafx.scene.chart.XYChart;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BidHistorySeriesBuilder {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private BidHistorySeriesBuilder() {}

    public static XYChart.Series<String, Number> buildSeries(List<Bid> bids) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Bid Price");

        if (bids == null || bids.isEmpty()) {
            return series;
        }

        Map<String, Integer> labelCountMap = new HashMap<>();

        for (Bid bid : bids) {
            if (bid == null) {
                continue;
            }

            String timeLabel = buildTimeLabel(bid, labelCountMap);
            Number amount = bid.getAmount();

            series.getData().add(new XYChart.Data<>(timeLabel, amount));
        }

        return series;
    }

    private static String buildTimeLabel(Bid bid, Map<String, Integer> labelCountMap) {
        String baseLabel = formatBidTime(bid.getCreatedAt());

        int count = labelCountMap.getOrDefault(baseLabel, 0) + 1;
        labelCountMap.put(baseLabel, count);

        if (count == 1) {
            return baseLabel;
        }

        return baseLabel + " #" + count;
    }

    private static String formatBidTime(LocalDateTime bidTime) {
        if (bidTime == null) {
            return "Unknown";
        }

        return bidTime.format(TIME_FORMATTER);
    }
}
