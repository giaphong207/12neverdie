package com.auction.integration;

import com.auction.server.service.BidOutcome;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;
import com.auction.shared.model.bid.BidSource;
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

@DisplayName("SIMULATION TEST - kịch bản đấu giá phức tạp")
class AuctionSimulationTest {

    private EmbeddedTestServer server;
    private static final String SELLER_ID = "u-seller-sim";

    @BeforeEach
    void setUp() throws IOException {
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
        server.userDao.updateBalance(brokeBidder, 0L);

        server.lifecycleService.scheduleClose(auction);
        Thread.sleep(1000);

        Auction finalState = server.auctionDao.findById(auctionId).orElseThrow();
        assertEquals(AuctionStatus.FINISHED, finalState.getStatus());
        assertEquals(0L, server.walletService.getBalance(brokeBidder));
        assertEquals(0L, server.walletService.getBalance(SELLER_ID));
    }

    @Test
    @DisplayName("MÔ PHỎNG: sniping war — 5 bid liên tiếp trong cửa sổ → endTime gia hạn nhiều lần")
    void simulation_sniping_war_extends_multiple_times() {
        String auctionId = "auction-sim-sniping";
        for (int i = 0; i < 5; i++) {
            server.userDao.save(new Bidder("snip-" + i, "sniper" + i, "pwd", 100_000_000L));
        }

        Auction auction = new Auction(
                auctionId, "item-sim", SELLER_ID,
                5_000_000L, 100_000L,
                AuctionStatus.RUNNING,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plus(Duration.ofMillis(50))
        );
        server.auctionDao.save(auction);

        LocalDateTime originalEnd = auction.getEndTime();

        long price = 5_100_000L;
        int extensionCount = 0;
        for (int i = 0; i < 5; i++) {
            BidOutcome outcome = server.bidService.placeBid(
                    auctionId, "snip-" + i, price);
            if (outcome.extendedSeconds() > 0) {
                extensionCount++;
            }
            price += 100_000L;
            try { Thread.sleep(60); } catch (InterruptedException ignored) {}
        }

        assertTrue(extensionCount >= 1, "Phải có ít nhất 1 bid fire anti-sniping");

        Duration totalExtended = Duration.between(originalEnd, auction.getEndTime());
        assertTrue(totalExtended.toMillis() >= extensionCount * 100L);

        long lastSuccessfulAmount = auction.getBidHistory()
                .get(auction.getBidHistory().size() - 1).getAmount();
        assertEquals(lastSuccessfulAmount, auction.getCurrentPrice());
    }

    @Test
    @DisplayName("MÔ PHỎNG: 5 auto-bid → winner = người max cao nhất, finalPrice = runnerUp + step")
    void simulation_autobid_cascade_5_bidders() {
        String auctionId = "auction-sim-cascade";

        Auction auction = new Auction(
                auctionId, "item-sim", SELLER_ID,
                5_000_000L, 100_000L,
                AuctionStatus.RUNNING,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusMinutes(30)
        );
        server.auctionDao.save(auction);

        long[] maxAmounts = {5_500_000L, 6_000_000L, 6_500_000L, 7_000_000L, 8_000_000L};
        for (int i = 0; i < 5; i++) {
            String bidderId = "ab-" + i;
            server.userDao.save(new Bidder(bidderId, "autobidder" + i, "pwd", 100_000_000L));
            server.autoBidService.upsertConfig(auctionId, bidderId, maxAmounts[i], 100_000L);
        }

        String triggerBidder = "ab-trigger";
        server.userDao.save(new Bidder(triggerBidder, "trigger", "pwd", 100_000_000L));

        BidOutcome outcome = server.bidService.placeBid(
                auctionId, triggerBidder, 5_100_000L);

        Auction finalAuction = outcome.auction();

        assertEquals("ab-4", finalAuction.getHighestBidderId());
        assertEquals(7_100_000L, finalAuction.getCurrentPrice());

        long autoBidCount = finalAuction.getBidHistory().stream()
                .filter(b -> b.getSource() == BidSource.AUTO)
                .count();
        long manualBidCount = finalAuction.getBidHistory().stream()
                .filter(b -> b.getSource() == BidSource.MANUAL)
                .count();
        assertEquals(1, manualBidCount);
        assertTrue(autoBidCount >= 1);
    }
}
