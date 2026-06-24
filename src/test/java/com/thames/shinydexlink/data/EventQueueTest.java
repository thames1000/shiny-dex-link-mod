package com.thames.shinydexlink.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thames.shinydexlink.api.dto.CatchEventRequest;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class EventQueueTest {
    @TempDir
    Path tempDir;

    @Test
    void deduplicatesAndPersistsByEventId() throws Exception {
        UUID uuid = UUID.randomUUID();
        CatchEventRequest event = new CatchEventRequest();
        event.eventId = "server:" + uuid + ":1:mareep:abcd";
        event.minecraftUuid = uuid.toString();
        event.species = "mareep";

        Path path = tempDir.resolve("event_queue.json");
        EventQueue queue = new EventQueue(path);
        queue.load();

        assertTrue(queue.enqueueIfAbsent(event, "backend down"));
        assertFalse(queue.enqueueIfAbsent(event, "still down"));
        assertEquals(1, queue.size());
        assertEquals(1, queue.countFor(uuid));

        EventQueue reloaded = new EventQueue(path);
        reloaded.load();
        assertEquals(1, reloaded.size());
    }
}
