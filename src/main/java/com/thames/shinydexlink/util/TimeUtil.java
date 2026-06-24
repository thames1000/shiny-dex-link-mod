package com.thames.shinydexlink.util;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {
    private TimeUtil() {
    }

    public static Instant now() {
        return Instant.now();
    }

    public static String isoNow() {
        return format(now());
    }

    public static String format(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }
}
