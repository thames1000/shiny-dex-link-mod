package com.thames.shinydexlink.sync;

import com.thames.shinydexlink.api.dto.CatchEventRequest;
import com.thames.shinydexlink.util.TimeUtil;
import java.time.Instant;
import java.util.UUID;

public final class CatchEventFactory {
    private CatchEventFactory() {
    }

    public static CatchEventRequest testEvent(String serverId, UUID playerUuid, String playerName) {
        CatchEventRequest request = new CatchEventRequest();
        request.serverId = serverId;
        request.minecraftUuid = playerUuid.toString();
        request.minecraftName = playerName;
        request.species = "mareep";
        request.displayName = "Mareep";
        request.shiny = true;
        request.form = "normal";
        request.gender = "female";
        request.level = 18;
        request.ball = "quick_ball";
        request.caughtAt = TimeUtil.isoNow();
        request.eventId = eventId(serverId, playerUuid, request.species, Instant.now());
        return request;
    }

    public static String eventId(String serverId, UUID playerUuid, String species, Instant caughtAt) {
        String cleanSpecies = species == null || species.isBlank() ? "unknown" : species;
        return serverId + ":" + playerUuid + ":" + caughtAt.toEpochMilli() + ":" + cleanSpecies + ":"
                + UUID.randomUUID().toString().substring(0, 8);
    }
}
