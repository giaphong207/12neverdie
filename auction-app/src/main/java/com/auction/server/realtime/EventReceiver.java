package com.auction.server.realtime;

public interface EventReceiver {
    void send(Object event);
}