package com.auction.client.chart;

import com.auction.shared.model.bid.Bid;
import com.auction.shared.model.bid.BidSource;
import javafx.scene.chart.XYChart;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BidHistorySeriesBuilderTest {

    @Test
    void shouldBuildSeriesFromBidHistory() {
        List<Bid> bids = List.of(
                createBid("bid-1", "auction-1", "bidder-1", 100_000,
                        LocalDateTime.of(2026, 5, 8, 21, 15, 10)),
                createBid("bid-2", "auction-1", "bidder-2", 150_000,
                        LocalDateTime.of(2026, 5, 8, 21, 15, 30))
        );

        XYChart.Series<String, Number> series =
                BidHistorySeriesBuilder.buildSeries(bids);

        assertEquals("Bid Price", series.getName());
        assertEquals(2, series.getData().size());

        assertEquals("21:15:10", series.getData().get(0).getXValue());
        assertEquals(100_000L, series.getData().get(0).getYValue());

        assertEquals("21:15:30", series.getData().get(1).getXValue());
        assertEquals(150_000L, series.getData().get(1).getYValue());
    }

    @Test
    void shouldReturnEmptySeriesWhenNoBidExists() {
        XYChart.Series<String, Number> series =
                BidHistorySeriesBuilder.buildSeries(List.of());

        assertEquals("Bid Price", series.getName());
        assertTrue(series.getData().isEmpty());
    }

    @Test
    void shouldReturnEmptySeriesWhenBidListIsNull() {
        XYChart.Series<String, Number> series =
                BidHistorySeriesBuilder.buildSeries(null);

        assertEquals("Bid Price", series.getName());
        assertTrue(series.getData().isEmpty());
    }

    @Test
    void shouldKeepPricesInCorrectOrder() {
        List<Bid> bids = List.of(
                createBid("bid-1", "auction-1", "bidder-1", 100_000,
                        LocalDateTime.of(2026, 5, 8, 21, 15, 10)),
                createBid("bid-2", "auction-1", "bidder-2", 150_000,
                        LocalDateTime.of(2026, 5, 8, 21, 15, 30)),
                createBid("bid-3", "auction-1", "bidder-3", 200_000,
                        LocalDateTime.of(2026, 5, 8, 21, 16, 0))
        );

        XYChart.Series<String, Number> series =
                BidHistorySeriesBuilder.buildSeries(bids);

        assertEquals(3, series.getData().size());
        assertEquals(100_000L, series.getData().get(0).getYValue());
        assertEquals(150_000L, series.getData().get(1).getYValue());
        assertEquals(200_000L, series.getData().get(2).getYValue());
    }

    @Test
    void shouldHandleDuplicateTimeLabels() {
        LocalDateTime sameTime = LocalDateTime.of(2026, 5, 8, 21, 15, 30);

        List<Bid> bids = List.of(
                createBid("bid-1", "auction-1", "bidder-1", 100_000, sameTime),
                createBid("bid-2", "auction-1", "bidder-2", 150_000, sameTime),
                createBid("bid-3", "auction-1", "bidder-3", 200_000, sameTime)
        );

        XYChart.Series<String, Number> series =
                BidHistorySeriesBuilder.buildSeries(bids);

        assertEquals(3, series.getData().size());

        assertEquals("21:15:30", series.getData().get(0).getXValue());
        assertEquals("21:15:30 #2", series.getData().get(1).getXValue());
        assertEquals("21:15:30 #3", series.getData().get(2).getXValue());
    }

    @Test
    void shouldSkipNullBid() {
        List<Bid> bids = new java.util.ArrayList<>();
        bids.add(createBid("bid-1", "auction-1", "bidder-1", 100_000,
                LocalDateTime.of(2026, 5, 8, 21, 15, 10)));
        bids.add(null);
        bids.add(createBid("bid-2", "auction-1", "bidder-2", 150_000,
                LocalDateTime.of(2026, 5, 8, 21, 15, 30)));

        XYChart.Series<String, Number> series =
                BidHistorySeriesBuilder.buildSeries(bids);

        assertEquals(2, series.getData().size());
        assertEquals(100_000L, series.getData().get(0).getYValue());
        assertEquals(150_000L, series.getData().get(1).getYValue());
    }

    private Bid createBid(
            String id,
            String auctionId,
            String bidderId,
            long amount,
            LocalDateTime createdAt
    ) {
        return new Bid(id, auctionId, bidderId, amount, createdAt, BidSource.MANUAL);
    }
}
