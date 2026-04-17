package com.auction.client.util;

public final class CountdownUtil {

    private CountdownUtil() {
    }

    public static String formatRemaining(java.time.Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return "Đã kết thúc";
        }

        long totalSeconds = duration.getSeconds();

        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;

        if (days > 0) {
            return String.format("%d ngày %02d:%02d:%02d", days, hours, minutes, seconds);
        }

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}