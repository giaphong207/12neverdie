package com.auction.shared.exception;

//Đây là lớp ngoại lệ cao nhất của dự án (Base Exception), mọi ngoại lệ tự định nghĩa khác đều phải kế thừa từ lớp này.
public class AppException extends RuntimeException {
    
    //Constructor nhận vào một thông báo lỗi (message)
    public AppException(String message) {
        super(message);
    }

    //Constructor nhận vào message và nguyên nhân gốc (Throwable cause)
    //dùng khi bạn muốn bọc một lỗi hệ thống (như lỗi Database) thành lỗi của mình
    public AppException(String message, Throwable cause) {
        super(message, cause);
    }
}