package com.thames.shinydexlink.hunt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

final class HuntManagerTest {
    @TempDir
    Path tempDir;

    private HuntManager newManager() throws Exception {
        HuntManager manager = new HuntManager(tempDir.resolve("hunts.json"), LoggerFactory.getLogger("test"));
        manager.load();
        return manager;
    }

    @Test
    void tracksAndPersistsTallies() throws Exception {
        UUID uuid = UUID.randomUUID();
        HuntManager manager = newManager();

        manager.setTarget(uuid, "Mareep", "Mareep", null, true, true);
        manager.recordEncounter(uuid);
        manager.recordEncounter(uuid);
        manager.recordEgg(uuid);
        manager.adjustManual(uuid, 3);

        HuntState state = manager.get(uuid).orElseThrow();
        assertEquals("mareep", state.species);
        assertEquals(2, state.encounters);
        assertEquals(1, state.eggs);
        assertEquals(6, state.total());
        assertEquals("mixed", state.kind());

        HuntManager reloaded = new HuntManager(tempDir.resolve("hunts.json"), LoggerFactory.getLogger("test"));
        reloaded.load();
        assertEquals(6, reloaded.get(uuid).orElseThrow().total());
    }

    @Test
    void respectsToggledAutoCounting() throws Exception {
        UUID uuid = UUID.randomUUID();
        HuntManager manager = newManager();
        manager.setTarget(uuid, "ralts", "Ralts", null, true, true);

        manager.toggleEncounters(uuid);
        manager.recordEncounter(uuid); // ignored: encounters off
        manager.recordEgg(uuid);

        HuntState state = manager.get(uuid).orElseThrow();
        assertEquals(0, state.encounters);
        assertEquals(1, state.eggs);
        assertEquals("eggs", state.kind());
        assertFalse(state.countEncounters);
    }

    @Test
    void manualNeverGoesNegativeAndResetClears() throws Exception {
        UUID uuid = UUID.randomUUID();
        HuntManager manager = newManager();
        manager.setTarget(uuid, "gible", "Gible", null, true, true);

        manager.adjustManual(uuid, -5);
        assertEquals(0, manager.get(uuid).orElseThrow().total());

        manager.recordEncounter(uuid);
        manager.reset(uuid);
        assertEquals(0, manager.get(uuid).orElseThrow().total());
    }

    @Test
    void matchesRespectsForm() {
        HuntState anyForm = new HuntState("vulpix", "Vulpix", null, true, true);
        assertTrue(anyForm.matches("vulpix", "alolan"));
        assertTrue(anyForm.matches("vulpix", "normal"));
        assertFalse(anyForm.matches("growlithe", "normal"));

        HuntState alolan = new HuntState("vulpix", "Vulpix", "alolan", true, true);
        assertTrue(alolan.matches("vulpix", "alolan"));
        assertFalse(alolan.matches("vulpix", "normal"));
    }

    @Test
    void stopRemovesHunt() throws Exception {
        UUID uuid = UUID.randomUUID();
        HuntManager manager = newManager();
        manager.setTarget(uuid, "eevee", "Eevee", null, true, true);
        manager.stop(uuid);
        assertTrue(manager.get(uuid).isEmpty());
    }
}
