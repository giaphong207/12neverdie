package com.auction.shared.model.bid;

/**
 * Phân biệt nguồn gốc của một bid.
 * - MANUAL: bidder tự bấm nút Đặt giá
 * - AUTO: hệ thống tự bid thay bidder dựa trên AutoBidConfig
 *
 * Lưu ý (theo phân công tuần 5):
 * Cái này KHÔNG bắt buộc, nhưng RẤT có ích cho debug và demo.
 * TV2 có thể dùng để vẽ chart phân màu manual/auto sau này.
 */
public enum BidSource {
    MANUAL,
    AUTO
}