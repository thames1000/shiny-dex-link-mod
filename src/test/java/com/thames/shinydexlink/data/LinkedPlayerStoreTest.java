package com.thames.shinydexlink.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LinkedPlayerStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsLinkedPlayers() throws Exception {
        UUID uuid = UUID.randomUUID();
        Path path = tempDir.resolve("linked_players.json");
        LinkedPlayerStore store = new LinkedPlayerStore(path);
        store.load();

        store.link(uuid, "Thamescape", "user_123");

        LinkedPlayerStore reloaded = new LinkedPlayerStore(path);
        reloaded.load();
        assertTrue(reloaded.isLinked(uuid));

        reloaded.unlink(uuid);
        assertFalse(reloaded.isLinked(uuid));
    }
}
