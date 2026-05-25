package com.auction.client.util;

import com.auction.shared.exception.AppExceptions.InvalidMoneyFormatException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MoneyParserTest {

    //TEST CASE HỢP LỆ 
    @Test
    public void shouldParseNormalNumber() {
        //Kiểm tra nhập số bình thường: "100000"
        long result = MoneyParser.parseBidAmount("100000");
        assertEquals(100000L, result);
    }

    @Test
    public void shouldParseNumberWithComma() {
        //Kiểm tra nhập có dấu phẩy: "100,000"
        long result = MoneyParser.parseBidAmount("100,000");
        assertEquals(100000L, result);
    }

    @Test
    public void shouldParseNumberWithSpace() {
        //Kiểm tra nhập có khoảng trắng: "100 000"
        long result = MoneyParser.parseBidAmount("100 000");
        assertEquals(100000L, result);
    }

    @Test
    public void shouldParseNumberWithLeadingAndTrailingSpace() {
        //Kiểm tra nhập có space đầu/cuối: "  100 000  "
        long result = MoneyParser.parseBidAmount("  100 000  ");
        assertEquals(100000L, result);
    }

    //TEST CASE LỖI
    @Test
    public void shouldThrowExceptionWhenInputIsEmpty() {
        //Kiểm tra trường hợp để trống: ""
        Exception exception = assertThrows(InvalidMoneyFormatException.class, () -> {
            MoneyParser.parseBidAmount("");
        });
        assertEquals("Vui lòng nhập số tiền!", exception.getMessage());
    }

    @Test
    public void shouldThrowExceptionWhenInputIsNull() {
        //Kiểm tra trường hợp null
        Exception exception = assertThrows(InvalidMoneyFormatException.class, () -> {
            MoneyParser.parseBidAmount(null);
        });
        assertEquals("Vui lòng nhập số tiền!", exception.getMessage());
    }

    @Test
    public void shouldThrowExceptionWhenInputIsZero() {
        //Kiểm tra trường hợp nhập 0
        Exception exception = assertThrows(InvalidMoneyFormatException.class, () -> {
            MoneyParser.parseBidAmount("0");
        });
        assertEquals("Số tiền đặt giá phải lớn hơn 0!", exception.getMessage());
    }

    @Test
    public void shouldThrowExceptionWhenInputIsNegative() {
        //Kiểm tra trường hợp nhập số âm: "-50000"
        Exception exception = assertThrows(InvalidMoneyFormatException.class, () -> {
            MoneyParser.parseBidAmount("-50000");
        });
        assertEquals("Số tiền đặt giá phải lớn hơn 0!", exception.getMessage());
    }

    @Test
    public void shouldThrowExceptionWhenInputContainsText() {
        //Kiểm tra trường hợp nhập chữ cái: "100k"
        Exception exception = assertThrows(InvalidMoneyFormatException.class, () -> {
            MoneyParser.parseBidAmount("100k");
        });
        assertEquals("Số tiền không hợp lệ! Vui lòng chỉ nhập số.", exception.getMessage());
    }

    @Test
    public void shouldThrowExceptionWhenInputContainsSpecialCharacters() {
        //Kiểm tra trường hợp nhập ký tự đặc biệt: "100@#$"
        Exception exception = assertThrows(InvalidMoneyFormatException.class, () -> {
            MoneyParser.parseBidAmount("100@#$");
        });
        assertEquals("Số tiền không hợp lệ! Vui lòng chỉ nhập số.", exception.getMessage());
    }

    @Test
    public void shouldThrowExceptionWhenInputIsOnlyText() {
        //Kiểm tra trường hợp nhập chỉ chữ: "abc"
        Exception exception = assertThrows(InvalidMoneyFormatException.class, () -> {
            MoneyParser.parseBidAmount("abc");
        });
        assertEquals("Số tiền không hợp lệ! Vui lòng chỉ nhập số.", exception.getMessage());
    }
}