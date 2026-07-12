package com.chessfantasy.israel.util;

import java.util.Locale;

public final class Format {

    private Format() {
    }

    public static String pawns(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    public static String timeLeft(long millis) {
        if (millis <= 0) return "ended";
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) return String.format(Locale.US, "%dh %02dm", hours, minutes);
        if (minutes > 0) return String.format(Locale.US, "%dm %02ds", minutes, seconds);
        return seconds + "s";
    }
}
