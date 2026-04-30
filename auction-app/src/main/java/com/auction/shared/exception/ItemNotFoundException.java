package com.auction.shared.exception;

public class ItemNotFoundException extends AppException{
    public ItemNotFoundException(String itemId){
        super("Không tìm thấy sản phẩm: " + itemId);
    }
}
