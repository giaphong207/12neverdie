package com.auction.server.service;

import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.bid.Bid;

public record BidResult(Auction auction, Bid bid) {
}
