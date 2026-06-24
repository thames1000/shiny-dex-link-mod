package com.thames.shinydexlink.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownManager {
    private final Map<String, Instant> lastUse = new ConcurrentHashMap<>();

    public OptionalLong remainingSeconds(UUID playerUuid, String action, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return OptionalLong.empty();
        }

        String key = playerUuid + ":" + action;
        Instant now = Instant.now();
        Instant previous = lastUse.get(key);
        if (previous == null) {
            lastUse.put(key, now);
            return OptionalLong.empty();
        }

        long elapsed = Duration.between(previous, now).toSeconds();
        if (elapsed >= cooldownSeconds) {
            lastUse.put(key, now);
            return OptionalLong.empty();
        }

        return OptionalLong.of(cooldownSeconds - elapsed);
    }
}
