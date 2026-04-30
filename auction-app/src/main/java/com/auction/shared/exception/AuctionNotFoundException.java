package com.auction.shared.exception;

public class AuctionNotFoundException extends AppException{
    public AuctionNotFoundException(String auctionId){
        super("Không tìm thấy phiên đấu giá: " + auctionId);
    }
}
