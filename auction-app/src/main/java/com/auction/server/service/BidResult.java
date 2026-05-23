package com.auction.server.service;

import com.auction.shared.model.Auction;
import com.auction.shared.model.Bid;

public record BidResult(Auction auction, Bid bid) {
}
