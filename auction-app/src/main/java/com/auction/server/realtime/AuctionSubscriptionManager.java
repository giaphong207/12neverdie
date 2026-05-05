package com.auction.server.realtime;

import com.auction.server.handler.ClientHandler;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionSubscriptionManager {
    // Danh sách những người đang ở màn hình AuctionList
    private final Set<ClientHandler> listSubscribers = ConcurrentHashMap.newKeySet();

    // Danh sách những người đang ở màn hình chi tiết của từng Auction (Map: AuctionId -> Set of Clients)
    private final ConcurrentHashMap<String, Set<ClientHandler>> auctionSubscribers = new ConcurrentHashMap<>();

    public void subscribeList(ClientHandler handler) {
        listSubscribers.add(handler);
    }

    public void subscribeAuction(String auctionId, ClientHandler handler) {
        auctionSubscribers.computeIfAbsent(auctionId, k -> ConcurrentHashMap.newKeySet()).add(handler);
    }

    public Set<ClientHandler> getListSubscribers() {
        return listSubscribers;
    }

    public Set<ClientHandler> getAuctionSubscribers(String auctionId) {
        return auctionSubscribers.getOrDefault(auctionId, ConcurrentHashMap.newKeySet());
    }

    // Khi client tắt app hoặc mất mạng, phải xóa họ khỏi danh sách để tránh lỗi
    public void remove(ClientHandler handler) {
        listSubscribers.remove(handler);
        for (Set<ClientHandler> subscribers : auctionSubscribers.values()) {
            subscribers.remove(handler);
        }
    }
}