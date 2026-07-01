package com.thames.shinydexlink;

import com.thames.shinydexlink.api.ShinyDexApiClient;
import com.thames.shinydexlink.cobblemon.CobblemonCaptureListener;
import com.thames.shinydexlink.cobblemon.CobblemonEvolutionListener;
import com.thames.shinydexlink.cobblemon.CobblemonHuntListener;
import com.thames.shinydexlink.command.ShinyDexCommand;
import com.thames.shinydexlink.config.ConfigManager;
import com.thames.shinydexlink.config.ShinyDexConfig;
import com.thames.shinydexlink.data.EventQueue;
import com.thames.shinydexlink.data.LinkedPlayerStore;
import com.thames.shinydexlink.hunt.HuntManager;
import com.thames.shinydexlink.net.HuntNetworking;
import com.thames.shinydexlink.sync.HuntProgressSync;
import com.thames.shinydexlink.sync.SyncService;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint, run on both physical sides. On a physical client it sets up the integrated
 * server side of hunts (so single-player and LAN hosts work); the HUD/keybind client half lives
 * in {@code com.thames.shinydexlink.client.ShinyDexLinkClient}.
 */
public final class ShinyDexLinkMod implements ModInitializer {
    public static final String MOD_ID = "shinydex-link";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Payload codecs must be registered on both sides before any networking happens.
        HuntNetworking.registerTypes();

        Path configDir = FabricLoader.getInstance().getConfigDir();
        ConfigManager configManager = new ConfigManager(configDir.resolve("shinydex-link.json"));
        ShinyDexConfig config = loadConfig(configManager);

        Path dataDir = configDir.resolve("shinydex-link");
        LinkedPlayerStore linkedPlayerStore = new LinkedPlayerStore(dataDir.resolve("linked_players.json"));
        EventQueue eventQueue = new EventQueue(dataDir.resolve("event_queue.json"));
        HuntManager huntManager = new HuntManager(dataDir.resolve("hunts.json"), LOGGER, config.maxConcurrentHunts);
        loadStores(linkedPlayerStore, eventQueue, huntManager);

        ShinyDexApiClient apiClient = new ShinyDexApiClient(config, LOGGER);
        SyncService syncService = new SyncService(config, apiClient, linkedPlayerStore, eventQueue, LOGGER);
        syncService.start();

        HuntProgressSync huntProgressSync = new HuntProgressSync(config, apiClient, linkedPlayerStore, huntManager, LOGGER);

        ShinyDexCommand command = new ShinyDexCommand(
                config, apiClient, linkedPlayerStore, eventQueue, huntManager, huntProgressSync, LOGGER);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> command.register(dispatcher));

        new HuntNetworking(huntManager, huntProgressSync, config, LOGGER).registerServerReceiver();

        new CobblemonCaptureListener(config, linkedPlayerStore, huntManager, syncService, LOGGER).register();
        new CobblemonHuntListener(config, linkedPlayerStore, huntManager, syncService, LOGGER).register();
        new CobblemonEvolutionListener(config, linkedPlayerStore, syncService, LOGGER).register();

        // Push a player's hunt progress to the website when they leave so it persists across sessions.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> huntProgressSync.pushOnDisconnect(handler.player));

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            syncService.stop();
            huntManager.save();
        });

        LOGGER.info("ShinyDex Link initialized. Config: {}", configManager.configPath());
    }

    private ShinyDexConfig loadConfig(ConfigManager configManager) {
        try {
            return configManager.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load ShinyDex Link config", exception);
        }
    }

    private void loadStores(LinkedPlayerStore linkedPlayerStore, EventQueue eventQueue, HuntManager huntManager) {
        try {
            linkedPlayerStore.load();
            eventQueue.load();
            huntManager.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load ShinyDex Link local data", exception);
        }
    }
}
