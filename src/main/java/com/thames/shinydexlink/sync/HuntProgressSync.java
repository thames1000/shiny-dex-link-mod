package com.thames.shinydexlink.sync;

import com.thames.shinydexlink.api.ShinyDexApiClient;
import com.thames.shinydexlink.api.dto.HuntFetchRequest;
import com.thames.shinydexlink.api.dto.HuntProgressDto;
import com.thames.shinydexlink.api.dto.HuntSyncRequest;
import com.thames.shinydexlink.config.ShinyDexConfig;
import com.thames.shinydexlink.data.LinkedPlayerStore;
import com.thames.shinydexlink.hunt.HuntManager;
import com.thames.shinydexlink.hunt.HuntState;
import com.thames.shinydexlink.net.HuntNetworking;
import com.thames.shinydexlink.util.TimeUtil;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Two-way hunt-progress sync with the website.
 *
 * <ul>
 *   <li>{@link #pushOnDisconnect} sends a player's full hunt snapshot when they leave, so their
 *       encounter counts persist server-side and follow them across sessions or devices. The list
 *       is authoritative — an empty snapshot clears their stored progress.</li>
 *   <li>{@link #fetchAndSeed} runs when a hunt starts: it asks the site for saved progress and, if
 *       the freshly started hunt hasn't been touched yet, seeds it so the counter resumes.</li>
 * </ul>
 *
 * Both calls are best-effort and asynchronous — a failure is logged and the local hunt (which is
 * still persisted to {@code hunts.json}) is unaffected.
 */
public final class HuntProgressSync {
    private final ShinyDexConfig config;
    private final ShinyDexApiClient apiClient;
    private final LinkedPlayerStore linkedPlayerStore;
    private final HuntManager huntManager;
    private final Logger logger;

    public HuntProgressSync(
            ShinyDexConfig config,
            ShinyDexApiClient apiClient,
            LinkedPlayerStore linkedPlayerStore,
            HuntManager huntManager,
            Logger logger
    ) {
        this.config = config;
        this.apiClient = apiClient;
        this.linkedPlayerStore = linkedPlayerStore;
        this.huntManager = huntManager;
        this.logger = logger;
    }

    private boolean enabled(UUID uuid) {
        return config.enabled
                && config.enableHuntCounter
                && config.syncHuntProgress
                && (linkedPlayerStore.isLinked(uuid) || config.syncUnlinkedPlayers);
    }

    /** Pushes the player's current hunts to the site as they disconnect. */
    public void pushOnDisconnect(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUUID();
        if (!enabled(uuid)) {
            return;
        }
        List<HuntState> hunts = huntManager.getAll(uuid);
        List<HuntProgressDto> dtos = new ArrayList<>(hunts.size());
        for (HuntState state : hunts) {
            dtos.add(toDto(state));
        }
        HuntSyncRequest request = new HuntSyncRequest(
                config.serverId, uuid.toString(), player.getGameProfile().getName(), dtos, TimeUtil.isoNow());

        apiClient.syncHunts(request).whenComplete((response, throwable) -> {
            if (throwable != null || response == null || !response.accepted()) {
                String error = throwable != null ? throwable.getMessage()
                        : response == null ? "empty response" : response.message;
                logger.warn("Failed to sync ShinyDex hunt progress for {}: {}", uuid, error);
            } else if (config.logSuccessfulSyncs) {
                logger.info("Synced {} hunt(s) for {} to ShinyDex on disconnect.", dtos.size(), uuid);
            }
        });
    }

    /**
     * Asks the site for saved progress for a just-started hunt and, if found, resumes the counter.
     * The seed is applied on the server thread and only when the hunt is still present and untouched
     * (total 0), so it never clobbers encounters the player racked up while the request was in flight.
     */
    public void fetchAndSeed(ServerPlayer player, String species, String form) {
        if (player == null || species == null) {
            return;
        }
        UUID uuid = player.getUUID();
        if (!enabled(uuid)) {
            return;
        }
        String key = HuntState.makeKey(species, form);
        HuntFetchRequest request = new HuntFetchRequest(
                config.serverId, uuid.toString(), player.getGameProfile().getName(), species, form);

        apiClient.fetchHunt(request).whenComplete((response, throwable) -> {
            if (throwable != null || response == null || !response.success || !response.found || response.hunt == null) {
                if (throwable != null) {
                    logger.debug("ShinyDex hunt fetch failed for {} {}", uuid, key, throwable);
                }
                return;
            }
            HuntProgressDto dto = response.hunt;
            MinecraftServer server = player.getServer();
            if (server == null) {
                return;
            }
            server.execute(() -> applySeed(player, uuid, key, dto));
        });
    }

    private void applySeed(ServerPlayer player, UUID uuid, String key, HuntProgressDto dto) {
        HuntState current = huntManager.find(uuid, key).orElse(null);
        if (current == null || current.total() != 0) {
            // Hunt was stopped, or the player already started counting — leave local progress alone.
            return;
        }
        huntManager.seedFromRemote(uuid, key, dto.encounters, dto.eggs, dto.manual,
                dto.countEncounters, dto.countEggs, parseInstant(dto.startedAt))
                .ifPresent(updated -> {
                    if (updated.total() <= 0) {
                        return;
                    }
                    HuntNetworking.sendUpdate(player, huntManager.getAll(uuid));
                    player.sendSystemMessage(Component.literal(
                            "Resumed your " + updated.displayName + " hunt from ShinyDex at " + updated.total() + "."));
                });
    }

    private HuntProgressDto toDto(HuntState state) {
        HuntProgressDto dto = new HuntProgressDto();
        dto.species = state.species;
        dto.form = state.form;
        dto.displayName = state.displayName;
        dto.encounters = state.encounters;
        dto.eggs = state.eggs;
        dto.manual = state.manual;
        dto.total = state.total();
        dto.countEncounters = state.countEncounters;
        dto.countEggs = state.countEggs;
        dto.startedAt = state.startedAt == null ? null : TimeUtil.format(state.startedAt);
        dto.updatedAt = state.updatedAt == null ? null : TimeUtil.format(state.updatedAt);
        return dto;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
