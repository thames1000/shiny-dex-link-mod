package com.thames.shinydexlink.cobblemon;

import com.thames.shinydexlink.api.dto.CatchEventRequest;
import com.thames.shinydexlink.api.dto.ShinyRemovalRequest;
import com.thames.shinydexlink.config.ShinyDexConfig;
import com.thames.shinydexlink.data.LinkedPlayerStore;
import com.thames.shinydexlink.sync.CatchEventFactory;
import com.thames.shinydexlink.sync.SyncService;
import com.thames.shinydexlink.util.TimeUtil;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Syncs a Pokémon's evolved form to the website, since evolving never fires a capture event and
 * would otherwise leave the evolved species (and its shiny state) missing from the dex.
 *
 * <p>On {@code EVOLUTION_COMPLETE} the evolved result is submitted as an ordinary catch for the new
 * species, mirroring {@link CobblemonCaptureListener} — the backend records the evolved species as
 * caught (and shiny-caught when the mon is shiny) exactly as it would for a real capture. Evolutions
 * don't touch hunt counters: a hunt tracks encounters/hatches of its target, not evolving an
 * already-owned mon into it.
 *
 * <p>Verified against Cobblemon 1.7.3:
 * <ul>
 *   <li>{@code com.cobblemon.mod.common.api.events.CobblemonEvents.EVOLUTION_COMPLETE}</li>
 *   <li>{@code com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionCompleteEvent}</li>
 *   <li>event property {@code pokemon} is the evolved result; {@code Pokemon.getOwnerPlayer()}
 *       yields the owning {@link ServerPlayer}.</li>
 * </ul>
 * Reflection keeps the mod buildable without a Cobblemon compile dependency; if the hook fails to
 * attach, captures and linking still work.
 */
public final class CobblemonEvolutionListener {
    private static final String EVENTS_CLASS = "com.cobblemon.mod.common.api.events.CobblemonEvents";
    private static final String EVOLUTION_EVENT_CLASS =
            "com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionCompleteEvent";
    private static final String EVOLUTION_EVENT_FIELD = "EVOLUTION_COMPLETE";

    private final ShinyDexConfig config;
    private final LinkedPlayerStore linkedPlayerStore;
    private final SyncService syncService;
    private final Logger logger;
    private Object subscription;

    public CobblemonEvolutionListener(
            ShinyDexConfig config,
            LinkedPlayerStore linkedPlayerStore,
            SyncService syncService,
            Logger logger
    ) {
        this.config = config;
        this.linkedPlayerStore = linkedPlayerStore;
        this.syncService = syncService;
        this.logger = logger;
    }

    public void register() {
        if (!config.enabled || !config.syncEvolutions) {
            return;
        }
        try {
            Class.forName(EVOLUTION_EVENT_CLASS);
            Class<?> eventsClass = Class.forName(EVENTS_CLASS);
            Object observable = eventsClass.getField(EVOLUTION_EVENT_FIELD).get(null);
            Method subscribe = observable.getClass().getMethod("subscribe", Consumer.class);
            subscription = subscribe.invoke(observable, (Consumer<Object>) this::onEvolutionComplete);
            logger.info("Registered ShinyDex Cobblemon evolution hook: {}.{}", EVENTS_CLASS, EVOLUTION_EVENT_FIELD);
        } catch (ClassNotFoundException exception) {
            logger.warn("Cobblemon 1.7.3 API was not found. Evolution sync is disabled, but commands and linking still work.");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.warn("Could not register ShinyDex Cobblemon evolution hook. Commands and linking still work.", exception);
        }
    }

    public Object subscription() {
        return subscription;
    }

    private void onEvolutionComplete(Object event) {
        if (!config.enabled || !config.syncEvolutions) {
            return;
        }

        try {
            // The event's "pokemon" is the evolved result; "sourcePokemon" is the pre-evolution.
            Object pokemon = CobblemonReflect.propertyOrNull(event, "pokemon");
            if (pokemon == null) {
                return;
            }
            // No player rides the evolution event, so resolve the owner off the mon itself. Skips
            // NPC-owned mons and owners who went offline mid-animation.
            if (!(CobblemonReflect.invokeOrNull(pokemon, "getOwnerPlayer") instanceof ServerPlayer player)) {
                return;
            }

            UUID uuid = player.getUUID();
            if (!linkedPlayerStore.isLinked(uuid) && !config.syncUnlinkedPlayers) {
                return;
            }

            Instant evolvedAt = Instant.now();
            String species = CobblemonReflect.speciesId(pokemon);
            CatchEventRequest request = new CatchEventRequest();
            request.eventId = CatchEventFactory.eventId(config.serverId, uuid, species, evolvedAt);
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
            // No pokeBallEntity here; ballName falls back to the mon's stored caughtBall.
            request.ball = CobblemonReflect.normalize(CobblemonReflect.ballName(null, pokemon));
            request.caughtAt = TimeUtil.format(evolvedAt);

            syncService.submitCatch(request, player);

            // Shininess carries through evolution, so a shiny result means the pre-evolution shiny
            // is gone from that slot. If it was the player's last shiny of that species, drop it
            // from their caught shinies so the dex tracks currently-owned shinies.
            if (request.shiny) {
                pruneEvolvedShiny(player, uuid, event, evolvedAt);
            }
        } catch (RuntimeException exception) {
            logger.warn("Failed to build ShinyDex catch event from Cobblemon evolution payload", exception);
        }
    }

    private void pruneEvolvedShiny(ServerPlayer player, UUID uuid, Object event, Instant evolvedAt) {
        if (!config.pruneEvolvedShinies) {
            return;
        }
        Object source = CobblemonReflect.propertyOrNull(event, "sourcePokemon");
        if (source == null) {
            return;
        }
        String sourceSpecies = CobblemonReflect.speciesId(source);
        // A form change that keeps the species (rather than a true evolution) would just re-add what
        // the catch above recorded — never prune the species we only re-caught.
        if (sourceSpecies == null || sourceSpecies.equals(CobblemonReflect.speciesId(
                CobblemonReflect.propertyOrNull(event, "pokemon")))) {
            return;
        }
        // The mon is already the evolved species here, so it can't match sourceSpecies; any hit is a
        // genuine other shiny of the pre-evolution species still in the party or PC.
        if (CobblemonReflect.countShiny(player, sourceSpecies) > 0) {
            return;
        }

        ShinyRemovalRequest removal = new ShinyRemovalRequest();
        removal.serverId = config.serverId;
        removal.minecraftUuid = uuid.toString();
        removal.minecraftName = player.getGameProfile().getName();
        removal.species = sourceSpecies;
        removal.form = CobblemonReflect.normalize(CobblemonReflect.formName(source));
        removal.aspects = CobblemonReflect.aspects(source);
        removal.reason = "evolved";
        removal.removedAt = TimeUtil.format(evolvedAt);
        syncService.submitShinyRemoval(removal, player);
    }
}
