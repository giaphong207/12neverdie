package com.auction.client.chart;

import com.auction.shared.model.bid.Bid;
import javafx.scene.chart.XYChart;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BidHistorySeriesBuilder {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Số quãng thời gian tối đa. Số điểm thực tế = số quãng CÓ bid (có thể ít hơn).
    private static final int BUCKET_COUNT = 25;

    private BidHistorySeriesBuilder() {}

    public static XYChart.Series<String, Number> buildSeries(List<Bid> bids) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Giá đặt");

        if (bids == null || bids.isEmpty()) {
            return series;
        }

        List<Bid> sampled = downsample(bids, BUCKET_COUNT);

        Map<String, Integer> labelCountMap = new HashMap<>();
        for (Bid bid : sampled) {
            String timeLabel = buildTimeLabel(bid, labelCountMap);
            XYChart.Data<String, Number> point = new XYChart.Data<>(timeLabel, bid.getAmount());
            point.setExtraValue(bid); // controller đọc Bid này để tô màu + tooltip
            series.getData().add(point);
        }

        return series;
    }

    // Chia phiên thành maxBuckets quãng đều theo thời gian; mỗi quãng lấy bid CUỐI (giá chốt).
    static List<Bid> downsample(List<Bid> bids, int maxBuckets) {
        List<Bid> cleaned = new ArrayList<>();
        for (Bid b : bids) {
            if (b != null && b.getCreatedAt() != null) cleaned.add(b);
        }

        int n = cleaned.size();
        if (n <= maxBuckets) {
            return cleaned;
        }

        LocalDateTime start = cleaned.get(0).getCreatedAt();
        LocalDateTime end = cleaned.get(n - 1).getCreatedAt();
        long span = Duration.between(start, end).toMillis();

        // Mọi bid gần như cùng một thời điểm -> không chia theo thời gian được.
        // Quay về lấy mẫu đều theo vị trí để vẫn thấy đường giá leo.
        if (span <= 0) {
            return sampleByIndex(cleaned, maxBuckets);
        }

        List<Bid> result = new ArrayList<>();
        int currentBucket = -1;
        Bid lastInBucket = null;
        for (Bid b : cleaned) {
            long offset = Duration.between(start, b.getCreatedAt()).toMillis();
            int bucket = (int) (offset * maxBuckets / span);
            if (bucket >= maxBuckets) bucket = maxBuckets - 1; // bid cuối rơi đúng mép phải
            if (bucket != currentBucket) {
                if (lastInBucket != null) result.add(lastInBucket); // chốt quãng trước
                currentBucket = bucket;
            }
            lastInBucket = b; // bid mới nhất trong quãng hiện tại
        }
        if (lastInBucket != null) result.add(lastInBucket); // chốt quãng cuối -> luôn có giá chốt

        return result;
    }

    private static List<Bid> sampleByIndex(List<Bid> bids, int maxPoints) {
        List<Bid> result = new ArrayList<>();
        int n = bids.size();
        double step = (double) (n - 1) / (maxPoints - 1);
        int lastIndex = -1;
        for (int i = 0; i < maxPoints; i++) {
            int idx = (int) Math.round(i * step);
            if (idx != lastIndex) {
                result.add(bids.get(idx));
                lastIndex = idx;
            }
        }
        return result;
    }

    private static String buildTimeLabel(Bid bid, Map<String, Integer> labelCountMap) {
        String baseLabel = formatBidTime(bid.getCreatedAt());
        int count = labelCountMap.getOrDefault(baseLabel, 0) + 1;
        labelCountMap.put(baseLabel, count);
        return count == 1 ? baseLabel : baseLabel + " #" + count;
    }

    private static String formatBidTime(LocalDateTime bidTime) {
        return bidTime == null ? "Unknown" : bidTime.format(TIME_FORMATTER);
    }
}