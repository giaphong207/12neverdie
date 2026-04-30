package com.auction.shared.exception;

//Ngoại lệ này ném ra khi Client mất kết nối với Server hoặc lỗi mạng.
public class NetworkException extends AppException {
    public NetworkException(String message) {
        super(message);
    }
    
    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
