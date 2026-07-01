package com.thames.shinydexlink.sync;

import com.thames.shinydexlink.api.ShinyDexApiClient;
import com.thames.shinydexlink.api.dto.ApiResponse;
import com.thames.shinydexlink.api.dto.CatchEventRequest;
import com.thames.shinydexlink.api.dto.ShinyRemovalRequest;
import com.thames.shinydexlink.config.ShinyDexConfig;
import com.thames.shinydexlink.data.EventQueue;
import com.thames.shinydexlink.data.LinkedPlayerStore;
import com.thames.shinydexlink.data.QueuedEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class SyncService {
    private final ShinyDexConfig config;
    private final ShinyDexApiClient apiClient;
    private final LinkedPlayerStore linkedPlayerStore;
    private final EventQueue eventQueue;
    private final Logger logger;
    private ScheduledExecutorService retryExecutor;

    public SyncService(
            ShinyDexConfig config,
            ShinyDexApiClient apiClient,
            LinkedPlayerStore linkedPlayerStore,
            EventQueue eventQueue,
            Logger logger
    ) {
        this.config = config;
        this.apiClient = apiClient;
        this.linkedPlayerStore = linkedPlayerStore;
        this.eventQueue = eventQueue;
        this.logger = logger;
    }

    public void start() {
        if (!config.retryFailedEvents || retryExecutor != null) {
            return;
        }

        retryExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "shinydex-link-retry");
            thread.setDaemon(true);
            return thread;
        });
        retryExecutor.scheduleWithFixedDelay(
                this::retryQueuedEvents,
                config.retryIntervalSeconds,
                config.retryIntervalSeconds,
                TimeUnit.SECONDS
        );
    }

    public void stop() {
        if (retryExecutor != null) {
            retryExecutor.shutdownNow();
            retryExecutor = null;
        }
    }

    public void submitCatch(CatchEventRequest event, ServerPlayer player) {
        if (!config.enabled) {
            return;
        }

        UUID uuid = player.getUUID();
        if (!linkedPlayerStore.isLinked(uuid) && !config.syncUnlinkedPlayers) {
            return;
        }

        apiClient.sendCatch(event).whenComplete((response, throwable) -> {
            if (throwable != null || response == null || !response.accepted()) {
                String error = throwable != null
                        ? throwable.getMessage()
                        : response == null ? "Empty ShinyDex response" : response.message;
                queue(event, error == null ? "Unknown ShinyDex sync failure" : error);
                sendOnServerThread(player, "ShinyDex is temporarily unavailable. Your catch was queued.");
                return;
            }

            markSynced(uuid, event.eventId);
            if (config.announceShinySyncToPlayer && event.shiny) {
                sendOnServerThread(player, "ShinyDex synced your shiny " + event.displayName + ".");
            }
        });
    }

    /**
     * Clears a species' shiny-caught state on the website after its last shiny evolved away. This
     * is best-effort: unlike catches it is not queued for retry, since a stale removal is far less
     * important than a missed catch and re-deriving it later would need another PC scan. A failure
     * only logs.
     */
    public void submitShinyRemoval(ShinyRemovalRequest request, ServerPlayer player) {
        if (!config.enabled) {
            return;
        }

        UUID uuid = player.getUUID();
        if (!linkedPlayerStore.isLinked(uuid) && !config.syncUnlinkedPlayers) {
            return;
        }

        apiClient.removeShiny(request).whenComplete((response, throwable) -> {
            if (throwable != null || response == null || !response.accepted()) {
                String error = throwable != null
                        ? throwable.getMessage()
                        : response == null ? "Empty ShinyDex response" : response.message;
                logger.warn("ShinyDex could not remove evolved-away shiny {}: {}",
                        request.species, error == null ? "Unknown ShinyDex failure" : error);
                return;
            }
            if (config.logSuccessfulSyncs) {
                logger.info("ShinyDex removed evolved-away shiny {}", request.species);
            }
        });
    }

    private void retryQueuedEvents() {
        if (!config.enabled || !config.retryFailedEvents) {
            return;
        }

        List<QueuedEvent> snapshot = eventQueue.snapshot();
        for (QueuedEvent queuedEvent : snapshot) {
            if (queuedEvent.event == null) {
                continue;
            }
            apiClient.sendCatch(queuedEvent.event).whenComplete((response, throwable) -> {
                try {
                    if (throwable == null && response != null && response.accepted()) {
                        eventQueue.remove(queuedEvent.event.eventId);
                        UUID uuid = UUID.fromString(queuedEvent.event.minecraftUuid);
                        linkedPlayerStore.updateLastSync(uuid, Instant.now());
                        if (config.logSuccessfulSyncs) {
                            logger.info("Retried ShinyDex event {}", queuedEvent.event.eventId);
                        }
                    } else {
                        String error = throwable != null
                                ? throwable.getMessage()
                                : response == null ? "Empty ShinyDex response" : response.message;
                        eventQueue.markAttempt(queuedEvent.event.eventId, error);
                    }
                } catch (RuntimeException | IOException exception) {
                    logger.warn("Failed to update ShinyDex retry queue for {}", queuedEvent.event.eventId, exception);
                }
            });
        }
    }

    private void queue(CatchEventRequest event, String error) {
        if (!config.retryFailedEvents) {
            logger.warn("ShinyDex event {} failed and retry queue is disabled: {}", event.eventId, error);
            return;
        }
        try {
            boolean queued = eventQueue.enqueueIfAbsent(event, error);
            if (queued) {
                logger.warn("Queued ShinyDex event {}: {}", event.eventId, error);
            }
        } catch (IOException exception) {
            logger.error("Failed to persist ShinyDex event {}", event.eventId, exception);
        }
    }

    private void markSynced(UUID uuid, String eventId) {
        try {
            linkedPlayerStore.updateLastSync(uuid, Instant.now());
            if (config.logSuccessfulSyncs) {
                logger.info("Synced ShinyDex event {}", eventId);
            }
        } catch (IOException exception) {
            logger.warn("Failed to update ShinyDex last sync time for {}", uuid, exception);
        }
    }

    private void sendOnServerThread(ServerPlayer player, String message) {
        if (player == null || player.getServer() == null) {
            return;
        }
        player.getServer().execute(() -> player.sendSystemMessage(Component.literal(message)));
    }
}
