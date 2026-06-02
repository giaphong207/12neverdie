package com.auction.integration;

import com.auction.server.service.BidOutcome;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;
import com.auction.shared.model.user.Bidder;
import com.auction.shared.model.user.Seller;
import com.auction.support.EmbeddedTestServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SIMULATION TEST — mô phỏng các kịch bản đấu giá thực tế phức tạp.
 */
@DisplayName("SIMULATION TEST - kịch bản đấu giá phức tạp")
class AuctionSimulationTest {

    private EmbeddedTestServer server;
    private static final String SELLER_ID = "u-seller-sim";

    @BeforeEach
    void setUp() throws IOException {
        // Anti-sniping: window 100ms, extension 1s (Duration.toSeconds yêu cầu >= 1)
        server = new EmbeddedTestServer(Duration.ofMillis(100), Duration.ofSeconds(1));
        server.userDao.save(new Seller(SELLER_ID, "seller_sim",
                BCrypt.hashpw("pwd", BCrypt.gensalt(4))));
        server.start();
    }

    @AfterEach
    void tearDown() { server.stop(); }

    @Test
    @DisplayName("MÔ PHỎNG: 1 phiên hoàn chỉnh từ RUNNING → bid → tự FINISHED + PAID")
    void simulation_full_auction_lifecycle() throws Exception {
        String auctionId = "auction-sim-lifecycle";
        String bidderId = "u-bidder-rich";
        long initialBidderBalance = 50_000_000L;
        long initialSellerBalance = 0L;

        server.userDao.save(new Bidder(bidderId, "bidder_rich", "pwd", initialBidderBalance));

        Auction auction = new Auction(
                auctionId, "item-sim", SELLER_ID,
                5_000_000L, 100_000L,
                AuctionStatus.RUNNING,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plus(Duration.ofMillis(500))
        );
        server.auctionDao.save(auction);
        server.lifecycleService.scheduleClose(auction);

        BidOutcome outcome = server.bidService.placeBid(auctionId, bidderId, 6_000_000L);
        assertEquals(6_000_000L, outcome.auction().getCurrentPrice());
        assertEquals(0L, outcome.extendedSeconds());

        Thread.sleep(1000);

        Auction finalState = server.auctionDao.findById(auctionId).orElseThrow();
        assertEquals(AuctionStatus.PAID, finalState.getStatus());
        assertEquals(bidderId, finalState.getWinnerBidderId());

        long bidderAfter = server.walletService.getBalance(bidderId);
        long sellerAfter = server.walletService.getBalance(SELLER_ID);

        assertEquals(initialBidderBalance - 6_000_000L, bidderAfter);
        assertEquals(initialSellerBalance + 6_000_000L, sellerAfter);

        assertEquals(initialBidderBalance + initialSellerBalance,
                bidderAfter + sellerAfter);
    }

    @Test
    @DisplayName("MÔ PHỎNG: winner THIẾU tiền → giữ FINISHED, không trừ ví")
    void simulation_broke_winner_keeps_finished() throws Exception {
        String auctionId = "auction-sim-broke";
        String brokeBidder = "u-broke";
        server.userDao.save(new Bidder(brokeBidder, "broke", "pwd", 5_500_000L));

        Auction auction = new Auction(
                auctionId, "item-sim", SELLER_ID,
                5_000_000L, 100_000L,
                AuctionStatus.RUNNING,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plus(Duration.ofMillis(500))
        );
        server.auctionDao.save(auction);

        server.bidService.placeBid(auctionId, brokeBidder, 5_500_000L);

        // Mô phỏng: ví bidder bị thanh toán ở giao dịch khác → còn 0đ
        server.userDao.updateBalance(brokeBidder, 0L);

        server.lifecycleService.scheduleClose(auction);
        Thread.sleep(1000);

        Auction finalState = server.auctionDao.findById(auctionId).orElseThrow();
        assertEquals(AuctionStatus.FINISHED, finalState.getStatus());
        assertEquals(0L, server.walletService.getBalance(brokeBidder));
        assertEquals(0L, server.walletService.getBalance(SELLER_ID));
    }
}
