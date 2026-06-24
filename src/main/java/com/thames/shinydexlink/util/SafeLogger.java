package com.thames.shinydexlink.util;

public final class SafeLogger {
    private SafeLogger() {
    }

    public static String redactToken(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
}
