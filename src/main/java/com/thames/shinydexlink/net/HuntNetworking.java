package com.thames.shinydexlink.net;

import com.thames.shinydexlink.cobblemon.SpeciesLookup;
import com.thames.shinydexlink.config.ShinyDexConfig;
import com.thames.shinydexlink.hunt.HuntManager;
import com.thames.shinydexlink.hunt.HuntState;
import com.thames.shinydexlink.sync.HuntProgressSync;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Wires up the hunt payload types and the server-side handler for client hunt actions.
 *
 * <p>{@link #registerTypes()} runs on both physical sides (from the common initializer) so the
 * codecs match; {@link #registerServerReceiver} runs wherever a server exists (dedicated or the
 * integrated server inside single-player).
 */
public final class HuntNetworking {
    private final HuntManager huntManager;
    private final HuntProgressSync huntProgressSync;
    private final ShinyDexConfig config;
    private final Logger logger;

    public HuntNetworking(HuntManager huntManager, HuntProgressSync huntProgressSync, ShinyDexConfig config, Logger logger) {
        this.huntManager = huntManager;
        this.huntProgressSync = huntProgressSync;
        this.config = config;
        this.logger = logger;
    }

    /** Registers payload codecs. Safe to call on either physical side; must be called on both. */
    public static void registerTypes() {
        PayloadTypeRegistry.playS2C().register(HuntUpdatePayload.TYPE, HuntUpdatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HuntActionPayload.TYPE, HuntActionPayload.CODEC);
    }

    public void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(HuntActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> handle(player, payload));
        });
    }

    private void handle(ServerPlayer player, HuntActionPayload payload) {
        if (!config.enableHuntCounter) {
            return;
        }
        UUID uuid = player.getUUID();
        String key = payload.arg();
        try {
            switch (payload.action()) {
                case HuntActionPayload.ACTION_INCREMENT -> huntManager.adjustManual(uuid, key, 1);
                case HuntActionPayload.ACTION_DECREMENT -> huntManager.adjustManual(uuid, key, -1);
                case HuntActionPayload.ACTION_RESET -> huntManager.reset(uuid, key);
                case HuntActionPayload.ACTION_TOGGLE_EGGS -> huntManager.toggleEggs(uuid, key);
                case HuntActionPayload.ACTION_TOGGLE_ENCOUNTERS -> huntManager.toggleEncounters(uuid, key);
                case HuntActionPayload.ACTION_STOP -> huntManager.stop(uuid, key);
                case HuntActionPayload.ACTION_STOP_ALL -> huntManager.stopAll(uuid);
                case HuntActionPayload.ACTION_SET_TARGET -> setTarget(player, key);
                default -> logger.debug("Ignoring unknown ShinyDex hunt action: {}", payload.action());
            }
        } catch (RuntimeException exception) {
            logger.warn("Failed to apply ShinyDex hunt action {} for {}", payload.action(), uuid, exception);
        }
        sendUpdate(player, huntManager.getAll(uuid));
    }

    private void setTarget(ServerPlayer player, String arg) {
        if (arg == null || arg.isBlank()) {
            return;
        }
        String species = arg;
        String form = null;
        int separator = arg.indexOf('|');
        if (separator >= 0) {
            species = arg.substring(0, separator);
            form = arg.substring(separator + 1);
        }
        HuntState state = huntManager.setTarget(player.getUUID(), species, SpeciesLookup.displayName(species), form,
                config.huntCountEncounters, config.huntCountEggHatches);
        if (state != null) {
            // Resume from any progress the website has saved for this species/form.
            huntProgressSync.fetchAndSeed(player, state.species, state.form);
        }
    }

    /** Pushes a snapshot of all the player's hunts; an empty list hides their overlay. */
    public static void sendUpdate(ServerPlayer player, List<HuntState> states) {
        if (player == null) {
            return;
        }
        ServerPlayNetworking.send(player, toPayload(states));
    }

    public static HuntUpdatePayload toPayload(List<HuntState> states) {
        if (states == null || states.isEmpty()) {
            return HuntUpdatePayload.inactive();
        }
        List<HuntUpdatePayload.Entry> entries = new java.util.ArrayList<>(states.size());
        for (HuntState state : states) {
            if (state == null || state.species == null) {
                continue;
            }
            entries.add(new HuntUpdatePayload.Entry(
                    state.species,
                    state.displayName,
                    state.form == null ? "" : state.form,
                    state.total(),
                    state.encounters,
                    state.eggs,
                    state.countEncounters,
                    state.countEggs
            ));
        }
        return new HuntUpdatePayload(entries);
    }
}
