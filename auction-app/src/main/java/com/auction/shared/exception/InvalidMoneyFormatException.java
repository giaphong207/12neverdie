package com.auction.shared.exception;

//Ngoại lệ ném ra khi định dạng tiền tệ nhập vào không hợp lệ.
public class InvalidMoneyFormatException extends AppException {
    public InvalidMoneyFormatException(String message) {
        super(message);
    }
}