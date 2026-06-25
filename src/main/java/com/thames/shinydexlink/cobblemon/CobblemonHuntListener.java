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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Auto-increments hunt counters from Cobblemon battle and egg events.
 *
 * <ul>
 *   <li>{@code BATTLE_STARTED_PRE} - counts an encounter when a player enters a wild battle
 *       against their hunt target. Wild spawns alone carry no owning player, so the battle is
 *       the first point a sighting can be attributed to a specific hunter.</li>
 *   <li>{@code HATCH_EGG_POST} - counts an egg hatch of the target species, and (since hatching
 *       never fires a capture) also completes an egg hunt when a shiny target hatches.</li>
 * </ul>
 *
 * Verified against Cobblemon 1.7.3. Reflection keeps the mod buildable without a Cobblemon
 * compile dependency; if these hooks fail to attach, the manual keybind still works.
 */
public final class CobblemonHuntListener {
    private static final String EVENTS_CLASS = "com.cobblemon.mod.common.api.events.CobblemonEvents";
    private static final String BATTLE_STARTED_FIELD = "BATTLE_STARTED_PRE";
    private static final String HATCH_EGG_FIELD = "HATCH_EGG_POST";

    private final ShinyDexConfig config;
    private final LinkedPlayerStore linkedPlayerStore;
    private final HuntManager huntManager;
    private final SyncService syncService;
    private final Logger logger;

    public CobblemonHuntListener(
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
        if (!config.enableHuntCounter) {
            return;
        }
        try {
            Class<?> eventsClass = Class.forName(EVENTS_CLASS);
            boolean battles = subscribe(eventsClass, BATTLE_STARTED_FIELD, this::onBattleStarted);
            boolean eggs = subscribe(eventsClass, HATCH_EGG_FIELD, this::onEggHatched);
            if (battles || eggs) {
                logger.info("Registered ShinyDex Cobblemon hunt hooks (battles={}, eggs={}).", battles, eggs);
            }
        } catch (ClassNotFoundException exception) {
            logger.warn("Cobblemon API not found. Hunt auto-increment is disabled; the manual keybind still works.");
        } catch (RuntimeException exception) {
            logger.warn("Could not register ShinyDex hunt hooks. The manual keybind still works.", exception);
        }
    }

    private boolean subscribe(Class<?> eventsClass, String fieldName, Consumer<Object> handler) {
        try {
            Object observable = eventsClass.getField(fieldName).get(null);
            Method subscribe = observable.getClass().getMethod("subscribe", Consumer.class);
            subscribe.invoke(observable, handler);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.warn("Could not subscribe to Cobblemon {} for ShinyDex hunts.", fieldName, exception);
            return false;
        }
    }

    private void onBattleStarted(Object event) {
        if (!config.enableHuntCounter) {
            return;
        }
        try {
            Object battle = CobblemonReflect.propertyOrNull(event, "battle");
            if (battle == null) {
                return;
            }
            List<Species> wild = wildPokemon(battle);
            if (wild.isEmpty()) {
                return;
            }
            for (ServerPlayer player : players(battle)) {
                UUID uuid = player.getUUID();
                HuntState state = huntManager.get(uuid).orElse(null);
                if (state == null || !state.countEncounters) {
                    continue;
                }
                for (Species species : wild) {
                    if (state.matches(species.id, species.form)) {
                        huntManager.recordEncounter(uuid)
                                .ifPresent(updated -> HuntNetworking.sendUpdate(player, updated));
                        break; // one encounter per battle
                    }
                }
            }
        } catch (RuntimeException exception) {
            logger.debug("Failed to process Cobblemon battle for ShinyDex hunts", exception);
        }
    }

    private void onEggHatched(Object event) {
        if (!config.enableHuntCounter) {
            return;
        }
        try {
            Object player = CobblemonReflect.propertyOrNull(event, "player");
            Object pokemon = CobblemonReflect.propertyOrNull(event, "pokemon");
            if (!(player instanceof ServerPlayer serverPlayer) || pokemon == null) {
                return;
            }
            UUID uuid = serverPlayer.getUUID();
            String species = CobblemonReflect.speciesId(pokemon);
            String form = CobblemonReflect.normalize(CobblemonReflect.formName(pokemon));
            HuntState state = huntManager.get(uuid).orElse(null);
            if (state == null || !state.matches(species, form)) {
                return;
            }

            HuntState updated = huntManager.recordEgg(uuid).orElse(state);
            HuntNetworking.sendUpdate(serverPlayer, updated);

            if (CobblemonReflect.booleanProperty(pokemon, "shiny", false)) {
                completeEggHunt(serverPlayer, uuid, pokemon, species, form, updated);
            }
        } catch (RuntimeException exception) {
            logger.debug("Failed to process Cobblemon egg hatch for ShinyDex hunts", exception);
        }
    }

    private void completeEggHunt(ServerPlayer player, UUID uuid, Object pokemon, String species, String form, HuntState state) {
        if (linkedPlayerStore.isLinked(uuid) || config.syncUnlinkedPlayers) {
            Instant hatchedAt = Instant.now();
            CatchEventRequest request = new CatchEventRequest();
            request.eventId = CatchEventFactory.eventId(config.serverId, uuid, species, hatchedAt);
            request.serverId = config.serverId;
            request.minecraftUuid = uuid.toString();
            request.minecraftName = player.getGameProfile().getName();
            request.species = species;
            request.displayName = CobblemonReflect.displayName(pokemon);
            request.shiny = true;
            request.form = form;
            request.aspects = CobblemonReflect.aspects(pokemon);
            request.gender = CobblemonReflect.normalize(CobblemonReflect.enumName(CobblemonReflect.propertyOrNull(pokemon, "gender")));
            request.level = CobblemonReflect.intProperty(pokemon, "level");
            request.ball = null;
            request.caughtAt = TimeUtil.format(hatchedAt);
            request.huntCount = state.total();
            request.huntKind = state.kind();
            request.huntStartedAt = state.startedAt == null ? null : TimeUtil.format(state.startedAt);
            syncService.submitCatch(request, player);
        }
        huntManager.stop(uuid);
        HuntNetworking.sendUpdate(player, null);
    }

    private List<ServerPlayer> players(Object battle) {
        List<ServerPlayer> result = new ArrayList<>();
        Object players = CobblemonReflect.invokeOrNull(battle, "getPlayers");
        if (players instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                if (value instanceof ServerPlayer serverPlayer) {
                    result.add(serverPlayer);
                }
            }
        }
        return result;
    }

    private List<Species> wildPokemon(Object battle) {
        List<Species> result = new ArrayList<>();
        Object actors = CobblemonReflect.invokeOrNull(battle, "getActors");
        if (!(actors instanceof Iterable<?> iterable)) {
            return result;
        }
        for (Object actor : iterable) {
            String type = CobblemonReflect.enumName(CobblemonReflect.invokeOrNull(actor, "getType"));
            if (!"WILD".equals(type)) {
                continue;
            }
            Object pokemonList = CobblemonReflect.invokeOrNull(actor, "getPokemonList");
            if (!(pokemonList instanceof Iterable<?> battlePokemon)) {
                continue;
            }
            for (Object battleMon : battlePokemon) {
                Object pokemon = CobblemonReflect.invokeOrNull(battleMon, "getEffectedPokemon");
                if (pokemon == null) {
                    pokemon = CobblemonReflect.invokeOrNull(battleMon, "getOriginalPokemon");
                }
                if (pokemon != null) {
                    result.add(new Species(
                            CobblemonReflect.speciesId(pokemon),
                            CobblemonReflect.normalize(CobblemonReflect.formName(pokemon))));
                }
            }
        }
        return result;
    }

    private record Species(String id, String form) {
    }
}
