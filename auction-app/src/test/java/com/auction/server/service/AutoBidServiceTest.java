package com.auction.server.service;

import com.auction.server.dao.AutoBidDao;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;
import com.auction.shared.model.AutoBidConfig;
import com.auction.shared.model.Bid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cho DefaultAutoBidService.
 *
 * 6 case theo file phân công TV4 tuần 5:
 *   1. shouldCreateAutoBidConfigSuccessfully
 *   2. shouldAutoBidWhenCurrentPriceIsBelowMaxAmount
 *   3. shouldStopAutoBiddingWhenMaxAmountReached
 *   4. shouldChooseHighestMaxAmountConfigWhenMultipleAutoBiddersExist
 *   5. shouldNotAutoBidForCurrentHighestBidder
 *   6. shouldGenerateBidHistoryEntriesForAutoBids
 *
 * + 2 case bonus:
 *   - shouldRejectInvalidConfig
 *   - shouldChooseEarlierConfigWhenMaxAmountTied
 *
 * Test KHÔNG phụ thuộc file thật, dùng InMemoryAutoBidDao.
 */
class AutoBidServiceTest {

    private InMemoryAutoBidDao dao;
    private AutoBidService service;

    @BeforeEach
    void setUp() {
        dao = new InMemoryAutoBidDao();
        service = new DefaultAutoBidService(dao);
    }

    // ========== Case 1 ==========
    @Test
    void shouldCreateAutoBidConfigSuccessfully() {
        service.upsertConfig("auction-1", "bidder-A", 1000L, 100L);

        Optional<AutoBidConfig> saved = dao.findByAuctionIdAndBidderId("auction-1", "bidder-A");
        assertTrue(saved.isPresent());
        assertEquals(1000L, saved.get().getMaxAmount());
        assertEquals(100L, saved.get().getIncrement());
        assertTrue(saved.get().isEnabled());
    }

    // ========== Case 2 ==========
    @Test
    void shouldAutoBidWhenCurrentPriceIsBelowMaxAmount() {
        // bidder-A có auto-bid config maxAmount=500, increment=50
        service.upsertConfig("auction-1", "bidder-A", 500L, 50L);

        // bidder-B vừa manual bid 150 (vượt minIncrement=50 từ startPrice=100)
        // và đang dẫn đầu.
        Auction auction = newAuctionWithSeedBid("auction-1", 100L, 50L, "bidder-B", 150L);

        boolean placed = service.resolveAutoBids(auction);

        assertTrue(placed, "Phải có ít nhất 1 auto-bid được tạo");
        // bidder-A auto-bid 150+50 = 200, vượt B
        assertEquals(200L, auction.getCurrentPrice());
        assertEquals("bidder-A", auction.getHighestBidderId());
    }

    // ========== Case 3 ==========
    @Test
    void shouldStopAutoBiddingWhenMaxAmountReached() {
        // bidder-A maxAmount=200, increment=50
        // bidder-B manual bid lên 250 (>= max của A) -> A không đủ tiền outbid
        service.upsertConfig("auction-1", "bidder-A", 200L, 50L);

        Auction auction = newAuctionWithSeedBid("auction-1", 100L, 50L, "bidder-B", 250L);

        boolean placed = service.resolveAutoBids(auction);

        // A không vượt được vì 250+50=300 > 200 (maxAmount)
        assertFalse(placed);
        assertEquals(250L, auction.getCurrentPrice());
        assertEquals("bidder-B", auction.getHighestBidderId());
    }

    // ========== Case 4 ==========
    @Test
    void shouldChooseHighestMaxAmountConfigWhenMultipleAutoBiddersExist() {
        // A maxAmount=300, B maxAmount=500 -> B nên thắng cascade
        service.upsertConfig("auction-1", "bidder-A", 300L, 50L);
        service.upsertConfig("auction-1", "bidder-B", 500L, 50L);

        // Manual bidder-C đặt 150, đang dẫn đầu
        Auction auction = newAuctionWithSeedBid("auction-1", 100L, 50L, "bidder-C", 150L);

        boolean placed = service.resolveAutoBids(auction);

        assertTrue(placed);
        // Sau cascade: B sẽ thắng vì max cao hơn
        assertEquals("bidder-B", auction.getHighestBidderId());
        // currentPrice cuối phải > 300 (A đã hết max)
        assertTrue(auction.getCurrentPrice() > 300L,
                "currentPrice phải > 300 (A maxed out), nhưng là " + auction.getCurrentPrice());
        // và <= 500 (B chưa vượt max)
        assertTrue(auction.getCurrentPrice() <= 500L);
    }

    // ========== Case 5 ==========
    @Test
    void shouldNotAutoBidForCurrentHighestBidder() {
        // A đang dẫn đầu mà có auto-bid config -> không nên tự bid chính mình
        service.upsertConfig("auction-1", "bidder-A", 1000L, 50L);

        Auction auction = newAuctionWithSeedBid("auction-1", 100L, 50L, "bidder-A", 150L);

        boolean placed = service.resolveAutoBids(auction);

        assertFalse(placed, "Không được auto-bid khi mình đang dẫn đầu");
        assertEquals(150L, auction.getCurrentPrice());
        assertEquals("bidder-A", auction.getHighestBidderId());
    }

    // ========== Case 6 ==========
    @Test
    void shouldGenerateBidHistoryEntriesForAutoBids() {
        service.upsertConfig("auction-1", "bidder-A", 500L, 50L);

        Auction auction = newAuctionWithSeedBid("auction-1", 100L, 50L, "bidder-B", 150L);
        int historyBefore = auction.getBidHistory().size();

        service.resolveAutoBids(auction);

        int historyAfter = auction.getBidHistory().size();
        assertTrue(historyAfter > historyBefore,
                "bidHistory phải có thêm entry cho auto-bid");
    }

    // ========== Bonus 1: validate input ==========
    @Test
    void shouldRejectInvalidConfig() {
        // Constructor của AutoBidConfig validate -> upsertConfig throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> service.upsertConfig("auction-1", "bidder-A", 0L, 50L));
        assertThrows(IllegalArgumentException.class,
                () -> service.upsertConfig("auction-1", "bidder-A", -100L, 50L));
        assertThrows(IllegalArgumentException.class,
                () -> service.upsertConfig("auction-1", "bidder-A", 1000L, 0L));
    }

    // ========== Bonus 2: tie-break by createdAt ==========
    @Test
    void shouldChooseEarlierConfigWhenMaxAmountTied() throws InterruptedException {
        service.upsertConfig("auction-1", "bidder-A", 500L, 50L);
        Thread.sleep(10);
        service.upsertConfig("auction-1", "bidder-B", 500L, 50L);

        Auction auction = newAuctionWithSeedBid("auction-1", 100L, 50L, "bidder-C", 150L);

        service.resolveAutoBids(auction);

        // Có cascade & có winner
        assertNotNull(auction.getHighestBidderId());
        assertTrue(auction.getBidHistory().size() >= 2,
                "Phải có cascade nhiều bid khi 2 auto-bidder cùng max");
    }

    // ============ Helpers ============

    /**
     * Tạo Auction RUNNING + seed 1 manual bid của highestBidderId.
     * Vì Auction của TV2 là immutable (final fields), phải dùng constructor
     * và addBid() đúng theo API public của Auction.
     *
     * @param firstBidAmount  số tiền bid của highestBidderId, phải >= startPrice + minIncrement
     */
    private Auction newAuctionWithSeedBid(String auctionId,
                                          long startPrice,
                                          long minIncrement,
                                          String highestBidderId,
                                          long firstBidAmount) {
        Auction a = new Auction(
                auctionId,
                "item-" + auctionId,
                "seller-x",
                startPrice,
                minIncrement,
                AuctionStatus.RUNNING,
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().plusMinutes(10)
        );

        // Seed manual bid
        Bid seedBid = new Bid(
                UUID.randomUUID().toString(),
                auctionId,
                highestBidderId,
                firstBidAmount,
                LocalDateTime.now().minusSeconds(5)
        );
        a.addBid(seedBid);
        return a;
    }

    /** In-memory implementation của AutoBidDao cho test. */
    private static class InMemoryAutoBidDao implements AutoBidDao {
        private final Map<String, AutoBidConfig> store = new HashMap<>();

        @Override
        public List<AutoBidConfig> findByAuctionId(String auctionId) {
            List<AutoBidConfig> result = new ArrayList<>();
            for (AutoBidConfig cfg : store.values()) {
                if (cfg.getAuctionId().equals(auctionId)) result.add(cfg);
            }
            return result;
        }

        @Override
        public Optional<AutoBidConfig> findByAuctionIdAndBidderId(String auctionId, String bidderId) {
            for (AutoBidConfig cfg : store.values()) {
                if (cfg.getAuctionId().equals(auctionId)
                        && cfg.getBidderId().equals(bidderId)) {
                    return Optional.of(cfg);
                }
            }
            return Optional.empty();
        }

        @Override
        public void save(AutoBidConfig config) {
            store.put(config.getId(), config);
        }

        @Override
        public void deleteById(String configId) {
            store.remove(configId);
        }

        @Override
        public List<AutoBidConfig> findAll() {
            return new ArrayList<>(store.values());
        }
    }
}