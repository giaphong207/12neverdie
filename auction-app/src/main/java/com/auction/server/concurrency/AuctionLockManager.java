package com.auction.server.concurrency;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class AuctionLockManager {
    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    public ReentrantLock getLock(String auctionId) {
        return lockMap.computeIfAbsent(auctionId, id -> new ReentrantLock());
    }
}