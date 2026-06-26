package com.thames.shinydexlink.hunt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

final class HuntManagerTest {
    @TempDir
    Path tempDir;

    private HuntManager newManager() throws Exception {
        return newManager(10);
    }

    private HuntManager newManager(int maxHunts) throws Exception {
        HuntManager manager = new HuntManager(tempDir.resolve("hunts.json"), LoggerFactory.getLogger("test"), maxHunts);
        manager.load();
        return manager;
    }

    private static String key(String species, String form) {
        return HuntState.makeKey(species, form);
    }

    @Test
    void tracksAndPersistsTallies() throws Exception {
        UUID uuid = UUID.randomUUID();
        HuntManager manager = newManager();
        String key = key("mareep", null);

        manager.setTarget(uuid, "Mareep", "Mareep", null, true, true);
        manager.recordEncounter(uuid, key);
        manager.recordEncounter(uuid, key);
        manager.recordEgg(uuid, key);
        manager.adjustManual(uuid, key, 3);

        HuntState state = manager.find(uuid, key).orElseThrow();
        assertEquals("mareep", state.species);
        assertEquals(2, state.encounters);
        assertEquals(1, state.eggs);
        assertEquals(6, state.total());
        assertEquals("mixed", state.kind());

        HuntManager reloaded = new HuntManager(tempDir.resolve("hunts.json"), LoggerFactory.getLogger("test"), 10);
        reloaded.load();
        assertEquals(6, reloaded.find(uuid, key).orElseThrow().total());
    }

    @Test
    void respectsToggledAutoCounting() throws Exception {
        UUID uuid = UUID.randomUUID();
        HuntManager manager = newManager();
        String key = key("ralts", null);
        manager.setTarget(uuid, "ralts", "Ralts", null, true, true);

        manager.toggleEncounters(uuid, key);
        manager.recordEncounter(uuid, key); // ignored: encounters off
        manager.recordEgg(uuid, key);

        HuntState state = manager.find(uuid, key).orElseThrow();
        assertEquals(0, state.encounters);
        assertEquals(1, state.eggs);
        assertEquals("eggs", state.kind());
        assertFalse(state.countEncounters);
    }

    @Test
    void manualNeverGoesNegativeAndResetClears() throws Exception {
        UUID uuid = UUID.randomUUID();
        HuntManager manager = newManager();
        String key = key("gible", null);
        manager.setTarget(uuid, "gible", "Gible", null, true, true);

        manager.adjustManual(uuid, key, -5);
        assertEquals(0, manager.find(uuid, key).orElseThrow().total());

        manager.recordEncounter(uuid, key);
        manager.reset(uuid, key);
        assertEquals(0, manager.find(uuid, key).orElseThrow().total());
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
    void stopRemovesOneHuntButKeepsOthers() throws Exception {
        UUID uuid = UUID.randomUUID();
        HuntManager manager = newManager();
        manager.setTarget(uuid, "eevee", "Eevee", null, true, true);
        manager.setTarget(uuid, "ditto", "Ditto", null, true, true);

        assertTrue(manager.stop(uuid, key("eevee", null)));
        assertTrue(manager.find(uuid, key("eevee", null)).isEmpty());
        assertEquals(1, manager.size(uuid));
        assertTrue(manager.find(uuid, key("ditto", null)).isPresent());
    }

    @Test
    void runsSeveralHuntsAtOnceIncludingSameSpeciesDifferentForms() throws Exception {
        UUID uuid = UUID.randomUUID();
        HuntManager manager = newManager();

        manager.setTarget(uuid, "vulpix", "Vulpix", null, true, true);
        manager.setTarget(uuid, "vulpix", "Vulpix", "alolan", true, true);
        manager.setTarget(uuid, "mareep", "Mareep", null, true, true);

        assertEquals(3, manager.size(uuid));
        // Bumping one hunt does not touch the others.
        manager.adjustManual(uuid, key("vulpix", "alolan"), 5);
        assertEquals(5, manager.find(uuid, key("vulpix", "alolan")).orElseThrow().total());
        assertEquals(0, manager.find(uuid, key("vulpix", null)).orElseThrow().total());
        assertEquals(0, manager.find(uuid, key("mareep", null)).orElseThrow().total());
    }

    @Test
    void enforcesMaxConcurrentHunts() throws Exception {
        UUID uuid = UUID.randomUUID();
        HuntManager manager = newManager(2);

        assertTrue(manager.setTarget(uuid, "a_one", "A", null, true, true) != null);
        assertTrue(manager.setTarget(uuid, "b_two", "B", null, true, true) != null);
        assertNull(manager.setTarget(uuid, "c_three", "C", null, true, true));
        assertEquals(2, manager.size(uuid));

        // Re-targeting an existing hunt is allowed at the cap and resets it.
        manager.adjustManual(uuid, key("a_one", null), 4);
        assertTrue(manager.setTarget(uuid, "a_one", "A", null, true, true) != null);
        assertEquals(0, manager.find(uuid, key("a_one", null)).orElseThrow().total());
    }

    @Test
    void stopAllClearsEveryHunt() throws Exception {
        UUID uuid = UUID.randomUUID();
        HuntManager manager = newManager();
        manager.setTarget(uuid, "eevee", "Eevee", null, true, true);
        manager.setTarget(uuid, "ditto", "Ditto", null, true, true);

        assertEquals(2, manager.stopAll(uuid));
        assertEquals(0, manager.size(uuid));
        assertTrue(manager.getAll(uuid).isEmpty());
    }

    @Test
    void seedFromRemoteResumesTalliesAndStartTime() throws Exception {
        UUID uuid = UUID.randomUUID();
        HuntManager manager = newManager();
        String key = key("mareep", null);
        manager.setTarget(uuid, "mareep", "Mareep", null, true, true);

        java.time.Instant started = java.time.Instant.parse("2026-06-26T18:02:11Z");
        HuntState seeded = manager.seedFromRemote(uuid, key, 187, 0, 3, true, false, started).orElseThrow();

        assertEquals(187, seeded.encounters);
        assertEquals(3, seeded.manual);
        assertEquals(190, seeded.total());
        assertFalse(seeded.countEggs);
        assertEquals(started, seeded.startedAt);

        // Survives a reload, proving it persisted.
        HuntManager reloaded = new HuntManager(tempDir.resolve("hunts.json"), LoggerFactory.getLogger("test"), 10);
        reloaded.load();
        assertEquals(190, reloaded.find(uuid, key).orElseThrow().total());
    }

    @Test
    void migratesLegacySingleHuntFormat() throws Exception {
        UUID uuid = UUID.randomUUID();
        // Original on-disk shape: one hunt object per UUID, not a list.
        String legacy = "{\n"
                + "  \"" + uuid + "\": {\n"
                + "    \"species\": \"mareep\",\n"
                + "    \"displayName\": \"Mareep\",\n"
                + "    \"encounters\": 7,\n"
                + "    \"eggs\": 0,\n"
                + "    \"manual\": 0,\n"
                + "    \"countEncounters\": true,\n"
                + "    \"countEggs\": true\n"
                + "  }\n"
                + "}\n";
        Files.writeString(tempDir.resolve("hunts.json"), legacy);

        HuntManager manager = newManager();
        assertEquals(1, manager.size(uuid));
        assertEquals(7, manager.find(uuid, key("mareep", null)).orElseThrow().total());
    }
}
