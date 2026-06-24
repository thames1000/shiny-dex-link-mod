package com.thames.shinydexlink.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thames.shinydexlink.api.dto.CatchEventRequest;
import com.thames.shinydexlink.config.ShinyDexConfig;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

final class ShinyDexApiClientTest {
    @Test
    void catchPayloadSerializationRedactsServerTokenForLogs() {
        ShinyDexConfig config = new ShinyDexConfig();
        config.serverToken = "super-secret-token";
        ShinyDexApiClient client = new ShinyDexApiClient(config, LoggerFactory.getLogger("test"));

        CatchEventRequest event = new CatchEventRequest();
        event.eventId = "event-1";
        event.serverId = "cobbleverse-main";
        event.minecraftUuid = "uuid";
        event.minecraftName = "Thamescape";
        event.species = "mareep";
        event.displayName = "Mareep";
        event.shiny = true;
        event.caughtAt = "2026-06-24T20:15:31Z";

        String payload = client.serializeCatchPayloadForTest(event);

        assertTrue(payload.contains("\"serverToken\": \"<redacted>\""));
        assertTrue(payload.contains("\"species\": \"mareep\""));
        assertFalse(payload.contains("super-secret-token"));
    }
}
