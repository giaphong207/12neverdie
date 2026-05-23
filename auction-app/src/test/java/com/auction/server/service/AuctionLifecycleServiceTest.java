package com.auction.server.service;

import com.auction.server.dao.AuctionDao;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuctionLifecycleService - chuyển trạng thái theo thời gian")
class AuctionLifecycleServiceTest {

    private DefaultAuctionLifecycleService lifecycleService;

}