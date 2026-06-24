package com.thames.shinydexlink;

import com.thames.shinydexlink.api.ShinyDexApiClient;
import com.thames.shinydexlink.cobblemon.CobblemonCaptureListener;
import com.thames.shinydexlink.command.ShinyDexCommand;
import com.thames.shinydexlink.config.ConfigManager;
import com.thames.shinydexlink.config.ShinyDexConfig;
import com.thames.shinydexlink.data.EventQueue;
import com.thames.shinydexlink.data.LinkedPlayerStore;
import com.thames.shinydexlink.sync.SyncService;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShinyDexLinkMod implements DedicatedServerModInitializer {
    public static final String MOD_ID = "shinydex-link";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeServer() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        ConfigManager configManager = new ConfigManager(configDir.resolve("shinydex-link.json"));
        ShinyDexConfig config = loadConfig(configManager);

        Path dataDir = configDir.resolve("shinydex-link");
        LinkedPlayerStore linkedPlayerStore = new LinkedPlayerStore(dataDir.resolve("linked_players.json"));
        EventQueue eventQueue = new EventQueue(dataDir.resolve("event_queue.json"));
        loadStores(linkedPlayerStore, eventQueue);

        ShinyDexApiClient apiClient = new ShinyDexApiClient(config, LOGGER);
        SyncService syncService = new SyncService(config, apiClient, linkedPlayerStore, eventQueue, LOGGER);
        syncService.start();

        ShinyDexCommand command = new ShinyDexCommand(config, apiClient, linkedPlayerStore, eventQueue, LOGGER);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> command.register(dispatcher));

        new CobblemonCaptureListener(config, linkedPlayerStore, syncService, LOGGER).register();
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> syncService.stop());

        LOGGER.info("ShinyDex Link initialized. Config: {}", configManager.configPath());
    }

    private ShinyDexConfig loadConfig(ConfigManager configManager) {
        try {
            return configManager.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load ShinyDex Link config", exception);
        }
    }

    private void loadStores(LinkedPlayerStore linkedPlayerStore, EventQueue eventQueue) {
        try {
            linkedPlayerStore.load();
            eventQueue.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load ShinyDex Link local data", exception);
        }
    }
}
