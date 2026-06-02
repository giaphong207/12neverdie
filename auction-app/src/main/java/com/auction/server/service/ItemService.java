package com.auction.server.service;

import java.util.List;

import com.auction.shared.model.item.Item;
import com.auction.shared.model.item.ItemType;

public interface ItemService {
    List<Item> getItemsBySeller(String sellerId);

    Item addItem(String sellerId, String name, String description,
                 long startPrice, ItemType type);

    Item updateItem(String itemId, String sellerId, String name,
                    String description, long startPrice, ItemType type);

    void deleteItemAsAdmin(String itemId); //admin xoá mọi phiên vi phạm, huỷ phiên của sản phẩm rồi xoá sản phẩm, bỏ qua kiểm tra chủ sở hữu
    
    List<Item> getAllItems(); //admin xem toàn bộ sản phẩm 
}
