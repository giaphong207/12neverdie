package com.auction.shared.exception;

public final class AppExceptions {
    private AppExceptions() {}

    public static class AppException extends RuntimeException {
        public AppException(String message) { super(message);}
        public AppException(String message, Throwable cause) { super(message, cause);}
    }

    public static class AuctionClosedException extends AppException {
        public AuctionClosedException(String message) {
            super(message);
        }
        public AuctionClosedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class AuctionNotFoundException extends AppException{
        public AuctionNotFoundException(String auctionId){
            super("Không tìm thấy phiên đấu giá: " + auctionId);
        }
    }

    public static class DataAccessException extends AppException {
        public DataAccessException(String message) {
            super(message);
        }
        public DataAccessException(String message, Throwable cause) {
            super(message, cause);   // ← gọi đúng constructor của AppException
        }
    }
    public static class InvalidInputException extends AppException {
        public InvalidInputException(String message) {
            super(message);
        }
    }
    public static class InvalidBidException extends AppException {
        public InvalidBidException(String message) {
            super(message);
        }
        public InvalidBidException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class InvalidItemException extends AppException {
        public InvalidItemException(String message) {
            super(message);
        }
        public InvalidItemException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class InvalidMoneyFormatException extends AppException {
        public InvalidMoneyFormatException(String message) {
            super(message);
        }
        public InvalidMoneyFormatException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ItemNotFoundException extends AppException{
        public ItemNotFoundException(String itemId){
            super("Không tìm thấy sản phẩm: " + itemId);
        }
    }

    public static class NetworkException extends AppException {
        public NetworkException(String message) {
            super(message);
        }
        public NetworkException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    public static class AuthenticationException extends AppException {
        public AuthenticationException(String message) {
            super(message);
        }
    }
    public static class DuplicateUsernameException extends AppException {
        public DuplicateUsernameException(String username) {
            super("Tên đăng nhập đã tồn tại: " + username);
        }
    }
}
