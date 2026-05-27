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
        public AuctionExtendedEvent(Auction auction) {
            super(auction);
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
    public static class AuctionPaidEvent extends AuctionEvent {
        public AuctionPaidEvent(Auction auction) {
            super(auction);
        }
    }
    public static class AuctionCancelledEvent extends AuctionEvent {
        public AuctionCancelledEvent(Auction auction) {
            super(auction);
        }
    }
}
