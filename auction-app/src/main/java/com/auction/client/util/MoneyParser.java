package com.auction.client.util;

import com.auction.shared.exception.AppExceptions.InvalidMoneyFormatException;

public final class MoneyParser {
    
    private MoneyParser() {} //Không cho phép khởi tạo

    /**
     * Chuyển đổi chuỗi tiền tệ từ giao diện thành số long.
     * @param rawInput Chuỗi người dùng nhập vào (VD: "100,000" hoặc "100 000")
     * @return Số tiền hợp lệ (kiểu long)
     * @throws InvalidMoneyFormatException Nếu để trống, nhập chữ, hoặc số âm
     */
    public static long parseBidAmount(String rawInput) {
        //Kiểm tra rỗng
        if (rawInput == null || rawInput.trim().isEmpty()) {
            throw new InvalidMoneyFormatException("Vui lòng nhập số tiền!");
        }

        try {
            //Làm sạch dữ liệu: Xóa dấu phẩy và khoảng trắng
            String cleanInput = rawInput.replace(",", "").replace(" ", "").trim();
            
            //Ép kiểu về số
            long amount = Long.parseLong(cleanInput);

            //Kiểm tra logic âm/dương
            if (amount <= 0) {
                throw new InvalidMoneyFormatException("Số tiền đặt giá phải lớn hơn 0!");
            }
            
            return amount;
            
        } catch (NumberFormatException e) {
            //Bắt lỗi nếu ép kiểu thất bại (nhập chữ cái, ký tự đặc biệt)
            throw new InvalidMoneyFormatException("Số tiền không hợp lệ! Vui lòng chỉ nhập số.", e);
        }
    }
}