package com.thames.shinydexlink.cobblemon;

import com.thames.shinydexlink.api.dto.CatchEventRequest;
import com.thames.shinydexlink.config.ShinyDexConfig;
import com.thames.shinydexlink.data.LinkedPlayerStore;
import com.thames.shinydexlink.hunt.HuntManager;
import com.thames.shinydexlink.hunt.HuntState;
import com.thames.shinydexlink.net.HuntNetworking;
import com.thames.shinydexlink.sync.CatchEventFactory;
import com.thames.shinydexlink.sync.SyncService;
import com.thames.shinydexlink.util.TimeUtil;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class CobblemonCaptureListener {
    private static final String EVENTS_CLASS = "com.cobblemon.mod.common.api.events.CobblemonEvents";
    private static final String CAPTURE_EVENT_CLASS = "com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent";
    private static final String CAPTURE_EVENT_FIELD = "POKEMON_CAPTURED";

    private final ShinyDexConfig config;
    private final LinkedPlayerStore linkedPlayerStore;
    private final HuntManager huntManager;
    private final SyncService syncService;
    private final Logger logger;
    private Object subscription;

    public CobblemonCaptureListener(
            ShinyDexConfig config,
            LinkedPlayerStore linkedPlayerStore,
            HuntManager huntManager,
            SyncService syncService,
            Logger logger
    ) {
        this.config = config;
        this.linkedPlayerStore = linkedPlayerStore;
        this.huntManager = huntManager;
        this.syncService = syncService;
        this.logger = logger;
    }

    public void register() {
        /*
         * Verified against Cobblemon tag 1.7.3:
         * - com.cobblemon.mod.common.api.events.CobblemonEvents.POKEMON_CAPTURED
         * - com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent
         * - event payload properties: pokemon, player, pokeBallEntity
         *
         * Reflection keeps this mod buildable without bundling or pinning a Cobblemon
         * compile-time artifact. If Cobblemon changes this API, the rest of ShinyDex Link
         * still loads and the log points at the version-specific adapter.
         */
        try {
            Class.forName(CAPTURE_EVENT_CLASS);
            Class<?> eventsClass = Class.forName(EVENTS_CLASS);
            Object observable = eventsClass.getField(CAPTURE_EVENT_FIELD).get(null);
            Method subscribe = observable.getClass().getMethod("subscribe", Consumer.class);
            subscription = subscribe.invoke(observable, (Consumer<Object>) this::onCaptureEvent);
            logger.info("Registered ShinyDex Cobblemon capture hook: {}.{}", EVENTS_CLASS, CAPTURE_EVENT_FIELD);
        } catch (ClassNotFoundException exception) {
            logger.warn("Cobblemon 1.7.3 API was not found. Capture sync is disabled, but commands and linking still work.");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.warn("Could not register ShinyDex Cobblemon capture hook. Commands and linking still work.", exception);
        }
    }

    public Object subscription() {
        return subscription;
    }

    private void onCaptureEvent(Object event) {
        if (!config.enabled) {
            return;
        }

        try {
            ServerPlayer player = (ServerPlayer) CobblemonReflect.property(event, "player");
            Object pokemon = CobblemonReflect.property(event, "pokemon");
            Object pokeBallEntity = CobblemonReflect.propertyOrNull(event, "pokeBallEntity");
            if (player == null || pokemon == null) {
                return;
            }

            UUID uuid = player.getUUID();
            if (!linkedPlayerStore.isLinked(uuid) && !config.syncUnlinkedPlayers) {
                return;
            }

            Instant caughtAt = Instant.now();
            String species = CobblemonReflect.speciesId(pokemon);
            CatchEventRequest request = new CatchEventRequest();
            request.eventId = CatchEventFactory.eventId(config.serverId, uuid, species, caughtAt);
            request.serverId = config.serverId;
            request.minecraftUuid = uuid.toString();
            request.minecraftName = player.getGameProfile().getName();
            request.species = species;
            request.displayName = CobblemonReflect.displayName(pokemon);
            request.shiny = CobblemonReflect.booleanProperty(pokemon, "shiny", false);
            request.form = CobblemonReflect.normalize(CobblemonReflect.formName(pokemon));
            request.aspects = CobblemonReflect.aspects(pokemon);
            request.gender = CobblemonReflect.normalize(CobblemonReflect.enumName(CobblemonReflect.propertyOrNull(pokemon, "gender")));
            request.level = CobblemonReflect.intProperty(pokemon, "level");
            request.ball = CobblemonReflect.normalize(CobblemonReflect.ballName(pokeBallEntity, pokemon));
            request.caughtAt = TimeUtil.format(caughtAt);

            applyHuntCompletion(player, uuid, request);

            syncService.submitCatch(request, player);
        } catch (RuntimeException exception) {
            logger.warn("Failed to build ShinyDex catch event from Cobblemon capture payload", exception);
        }
    }

    /**
     * If this catch completes the player's active hunt, stamp the catch with the hunt tally,
     * clear the hunt, and hide the overlay. Encounter-based hunts finish on the catch itself;
     * egg hunts finish in {@link CobblemonHuntListener} since hatching never fires a capture.
     */
    private void applyHuntCompletion(ServerPlayer player, UUID uuid, CatchEventRequest request) {
        if (!config.enableHuntCounter) {
            return;
        }
        HuntState state = huntManager.get(uuid).orElse(null);
        if (state == null || !state.matches(request.species, request.form)) {
            return;
        }
        request.huntCount = state.total();
        request.huntKind = state.kind();
        request.huntStartedAt = state.startedAt == null ? null : TimeUtil.format(state.startedAt);
        huntManager.stop(uuid);
        HuntNetworking.sendUpdate(player, null);
    }
}
