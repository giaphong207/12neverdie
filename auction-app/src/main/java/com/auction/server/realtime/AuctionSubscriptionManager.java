package com.auction.server.realtime;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionSubscriptionManager {
    // Những người đang ở màn hình AuctionList
    private final Set<EventReceiver> listSubscribers = ConcurrentHashMap.newKeySet();

    // Map: AuctionId -> những người đang xem chi tiết phiên đó
    private final ConcurrentHashMap<String, Set<EventReceiver>> auctionSubscribers = new ConcurrentHashMap<>();

    public void subscribeList(EventReceiver subscriber) {
        listSubscribers.add(subscriber);
    }

    public void subscribeAuction(String auctionId, EventReceiver subscriber) {
        auctionSubscribers.computeIfAbsent(auctionId, k -> ConcurrentHashMap.newKeySet()).add(subscriber);
    }

    public Set<EventReceiver> getListSubscribers() {
        return listSubscribers;
    }

    public Set<EventReceiver> getAuctionSubscribers(String auctionId) {
        return auctionSubscribers.getOrDefault(auctionId, ConcurrentHashMap.newKeySet());
    }

    // Khi client tắt app / mất mạng, xóa khỏi mọi danh sách
    public void remove(EventReceiver subscriber) {
        listSubscribers.remove(subscriber);
        for (Set<EventReceiver> subscribers : auctionSubscribers.values()) {
            subscribers.remove(subscriber);
        }
    }
}