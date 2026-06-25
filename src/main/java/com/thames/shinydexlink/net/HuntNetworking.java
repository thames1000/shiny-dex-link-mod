package com.thames.shinydexlink.net;

import com.thames.shinydexlink.cobblemon.SpeciesLookup;
import com.thames.shinydexlink.config.ShinyDexConfig;
import com.thames.shinydexlink.hunt.HuntManager;
import com.thames.shinydexlink.hunt.HuntState;
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
    private final ShinyDexConfig config;
    private final Logger logger;

    public HuntNetworking(HuntManager huntManager, ShinyDexConfig config, Logger logger) {
        this.huntManager = huntManager;
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
        try {
            switch (payload.action()) {
                case HuntActionPayload.ACTION_INCREMENT -> huntManager.adjustManual(uuid, 1);
                case HuntActionPayload.ACTION_DECREMENT -> huntManager.adjustManual(uuid, -1);
                case HuntActionPayload.ACTION_RESET -> huntManager.reset(uuid);
                case HuntActionPayload.ACTION_TOGGLE_EGGS -> huntManager.toggleEggs(uuid);
                case HuntActionPayload.ACTION_TOGGLE_ENCOUNTERS -> huntManager.toggleEncounters(uuid);
                case HuntActionPayload.ACTION_STOP -> huntManager.stop(uuid);
                case HuntActionPayload.ACTION_SET_TARGET -> setTarget(uuid, payload.arg());
                default -> logger.debug("Ignoring unknown ShinyDex hunt action: {}", payload.action());
            }
        } catch (RuntimeException exception) {
            logger.warn("Failed to apply ShinyDex hunt action {} for {}", payload.action(), uuid, exception);
        }
        sendUpdate(player, huntManager.get(uuid).orElse(null));
    }

    private void setTarget(UUID uuid, String arg) {
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
        huntManager.setTarget(uuid, species, SpeciesLookup.displayName(species), form,
                config.huntCountEncounters, config.huntCountEggHatches);
    }

    /** Pushes a hunt snapshot to one player; a null state hides their overlay. */
    public static void sendUpdate(ServerPlayer player, HuntState state) {
        if (player == null) {
            return;
        }
        ServerPlayNetworking.send(player, toPayload(state));
    }

    public static HuntUpdatePayload toPayload(HuntState state) {
        if (state == null || state.species == null) {
            return HuntUpdatePayload.inactive();
        }
        return new HuntUpdatePayload(
                true,
                state.species,
                state.displayName,
                state.form == null ? "" : state.form,
                state.total(),
                state.encounters,
                state.eggs,
                state.countEncounters,
                state.countEggs
        );
    }
}
