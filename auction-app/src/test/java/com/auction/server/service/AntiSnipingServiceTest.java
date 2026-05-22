package com.auction.server.service;

import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AntiSnipingService - gia hạn 60s nếu bid trong phút cuối")
class AntiSnipingServiceTest {

    private DefaultAntiSnipingService service;
}