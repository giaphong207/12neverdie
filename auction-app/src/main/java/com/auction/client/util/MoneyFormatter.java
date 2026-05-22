package com.auction.client.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Format số tiền theo chuẩn VND Library Bronze: "1.250.000.000 ₫"
 * Dấu chấm phân cách hàng nghìn, ký hiệu ₫ ở cuối.
 */
public final class MoneyFormatter {

    private static final DecimalFormat VND_FORMATTER;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.');
        VND_FORMATTER = new DecimalFormat("#,##0", symbols);
    }

    private MoneyFormatter() {}

    /**
     * Format số tiền long → "1.250.000.000 ₫"
     */
    public static String formatVnd(long amount) {
        return VND_FORMATTER.format(amount) + " ₫";
    }

    /**
     * Format ngắn gọn (không có ₫): "1.250.000.000"
     */
    public static String formatVndShort(long amount) {
        return VND_FORMATTER.format(amount);
    }
}