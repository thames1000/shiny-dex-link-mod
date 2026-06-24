package com.thames.shinydexlink.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ConfigManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void loadCreatesDefaultConfig() throws Exception {
        Path configPath = tempDir.resolve("shinydex-link.json");
        ConfigManager manager = new ConfigManager(configPath);

        ShinyDexConfig config = manager.load();

        assertTrue(Files.exists(configPath));
        assertEquals("cobbleverse-main", config.serverId);
        assertEquals("mock", config.apiBaseUrl);
    }

    @Test
    void loadNormalizesInvalidTimingValues() throws Exception {
        Path configPath = tempDir.resolve("shinydex-link.json");
        Files.writeString(configPath, """
                {
                  "retryIntervalSeconds": 1,
                  "requestTimeoutSeconds": 0,
                  "linkCooldownSeconds": -5,
                  "testCooldownSeconds": -10
                }
                """);

        ShinyDexConfig config = new ConfigManager(configPath).load();

        assertEquals(5, config.retryIntervalSeconds);
        assertEquals(1, config.requestTimeoutSeconds);
        assertEquals(0, config.linkCooldownSeconds);
        assertEquals(0, config.testCooldownSeconds);
    }
}
