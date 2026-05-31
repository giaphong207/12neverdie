package com.auction.shared.networkMessage;

import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.bid.Bid;

import java.io.Serializable;

public class AuctionEvents {
    public static abstract class AuctionEvent implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Auction auction;

        public AuctionEvent(Auction auction) {
            this.auction = auction;
        }

        public Auction getAuction() {
            return auction;
        }
    }
    public static class AuctionStartedEvent extends AuctionEvent {
        public AuctionStartedEvent(Auction auction) {
            super(auction);
        }
    }
    public static class BidPlacedEvent extends AuctionEvent {
        private final Bid bid;
        public BidPlacedEvent(Auction auction, Bid bid) {
            super(auction);
            this.bid = bid;
        }

        public Bid getBid() {
            return bid;
        }
    }
    public static class AuctionExtendedEvent extends AuctionEvent {
        private final long extendedSeconds;
        public AuctionExtendedEvent(Auction auction, long extendedSeconds) {
            super(auction);
            this.extendedSeconds = extendedSeconds;
        }
        public long getExtendedSeconds() {
            return extendedSeconds;
        }
    }
    public static class AuctionEndedEvent extends AuctionEvent {
        public AuctionEndedEvent(Auction auction) {
            super(auction);
        }
    }
    public static class AuctionUpdatedEvent extends AuctionEvent {
        public AuctionUpdatedEvent(Auction auction) {
            super(auction);
        }
    }
    public static class AuctionCreatedEvent extends AuctionEvent {
        public AuctionCreatedEvent(Auction auction) { super(auction);}
    }
    public static class AuctionPaidEvent extends AuctionEvent {
        public AuctionPaidEvent(Auction auction) {
            super(auction);
        }
    }
    /**
     * Báo số dư ví mới sau khi phiên thanh toán xong: winner bị trừ, seller được cộng.
     * Client đối chiếu id của mình với winnerId/sellerId để cập nhật ví realtime.
     */
    public static class WalletUpdatedEvent extends AuctionEvent {
        private final String winnerId;
        private final long winnerBalance;
        private final String sellerId;
        private final long sellerBalance;

        public WalletUpdatedEvent(Auction auction, String winnerId, long winnerBalance,
                                  String sellerId, long sellerBalance) {
            super(auction);
            this.winnerId = winnerId;
            this.winnerBalance = winnerBalance;
            this.sellerId = sellerId;
            this.sellerBalance = sellerBalance;
        }

        public String getWinnerId() {
            return winnerId;
        }

        public long getWinnerBalance() {
            return winnerBalance;
        }

        public String getSellerId() {
            return sellerId;
        }

        public long getSellerBalance() {
            return sellerBalance;
        }
    }
    public static class AuctionCancelledEvent extends AuctionEvent {
        public AuctionCancelledEvent(Auction auction) {
            super(auction);
        }
    }
}
