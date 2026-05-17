package com.auction.shared.exception;

/**
 * Bọc tất cả SQLException để service không phải biết về JDBC.
 * Khi DB có vấn đề (mất kết nối, query sai,...) → throw class này.
 */
public class DataAccessException extends AppException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message + " (cause: " + cause.getMessage() + ")");
    }
}