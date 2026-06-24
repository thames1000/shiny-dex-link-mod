package com.thames.shinydexlink.cobblemon;

import com.thames.shinydexlink.api.dto.CatchEventRequest;
import com.thames.shinydexlink.config.ShinyDexConfig;
import com.thames.shinydexlink.data.LinkedPlayerStore;
import com.thames.shinydexlink.sync.CatchEventFactory;
import com.thames.shinydexlink.sync.SyncService;
import com.thames.shinydexlink.util.TimeUtil;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class CobblemonCaptureListener {
    private static final String EVENTS_CLASS = "com.cobblemon.mod.common.api.events.CobblemonEvents";
    private static final String CAPTURE_EVENT_CLASS = "com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent";
    private static final String CAPTURE_EVENT_FIELD = "POKEMON_CAPTURED";

    private final ShinyDexConfig config;
    private final LinkedPlayerStore linkedPlayerStore;
    private final SyncService syncService;
    private final Logger logger;
    private Object subscription;

    public CobblemonCaptureListener(
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
        /*
         * Verified against Cobblemon tag 1.7.3:
         * - com.cobblemon.mod.common.api.events.CobblemonEvents.POKEMON_CAPTURED
         * - com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent
         * - event payload properties: pokemon, player, pokeBallEntity
         *
         * Reflection keeps this server-side mod buildable without bundling or pinning a
         * Cobblemon compile-time artifact. If Cobblemon changes this API, the rest of
         * ShinyDex Link still loads and the log points at the version-specific adapter.
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
            ServerPlayer player = (ServerPlayer) property(event, "player");
            Object pokemon = property(event, "pokemon");
            Object pokeBallEntity = property(event, "pokeBallEntity");
            if (player == null || pokemon == null) {
                return;
            }

            UUID uuid = player.getUUID();
            if (!linkedPlayerStore.isLinked(uuid) && !config.syncUnlinkedPlayers) {
                return;
            }

            Instant caughtAt = Instant.now();
            String species = speciesId(pokemon);
            CatchEventRequest request = new CatchEventRequest();
            request.eventId = CatchEventFactory.eventId(config.serverId, uuid, species, caughtAt);
            request.serverId = config.serverId;
            request.minecraftUuid = uuid.toString();
            request.minecraftName = player.getGameProfile().getName();
            request.species = species;
            request.displayName = displayName(pokemon);
            request.shiny = booleanProperty(pokemon, "shiny", false);
            request.form = normalize(formName(pokemon));
            request.gender = normalize(enumName(propertyOrNull(pokemon, "gender")));
            request.level = intProperty(pokemon, "level");
            request.ball = normalize(ballName(pokeBallEntity, pokemon));
            request.caughtAt = TimeUtil.format(caughtAt);

            syncService.submitCatch(request, player);
        } catch (RuntimeException exception) {
            logger.warn("Failed to build ShinyDex catch event from Cobblemon capture payload", exception);
        }
    }

    private String speciesId(Object pokemon) {
        Object species = propertyOrNull(pokemon, "species");
        Object showdownId = invokeOrNull(species, "showdownId");
        if (showdownId != null) {
            return showdownId.toString();
        }

        Object identifier = propertyOrNull(species, "resourceIdentifier");
        if (identifier instanceof ResourceLocation resourceLocation) {
            return resourceLocation.getPath();
        }
        return "unknown";
    }

    private String displayName(Object pokemon) {
        Object component = invokeOrNull(pokemon, "getDisplayName", new Class<?>[]{boolean.class}, false);
        if (component instanceof Component minecraftComponent) {
            return minecraftComponent.getString();
        }

        Object species = propertyOrNull(pokemon, "species");
        Object name = propertyOrNull(species, "name");
        return name == null ? "Unknown" : name.toString();
    }

    private String formName(Object pokemon) {
        Object form = propertyOrNull(pokemon, "form");
        Object formName = propertyOrNull(form, "name");
        return formName == null ? "normal" : formName.toString();
    }

    private String ballName(Object pokeBallEntity, Object pokemon) {
        Object ball = propertyOrNull(pokeBallEntity, "pokeBall");
        if (ball == null) {
            ball = propertyOrNull(pokemon, "caughtBall");
        }

        Object name = propertyOrNull(ball, "name");
        if (name instanceof ResourceLocation resourceLocation) {
            return resourceLocation.getPath();
        }
        return name == null ? null : name.toString();
    }

    private boolean booleanProperty(Object target, String name, boolean fallback) {
        Object value = propertyOrNull(target, name);
        return value instanceof Boolean booleanValue ? booleanValue : fallback;
    }

    private Integer intProperty(Object target, String name) {
        Object value = propertyOrNull(target, name);
        return value instanceof Number number ? number.intValue() : null;
    }

    private String enumName(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return value.toString();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT).replace(' ', '_');
        int namespace = normalized.indexOf(':');
        return namespace >= 0 ? normalized.substring(namespace + 1) : normalized;
    }

    private Object property(Object target, String name) {
        Object value = propertyOrNull(target, name);
        if (value == null) {
            throw new IllegalStateException("Missing Cobblemon property " + name + " on " + target.getClass().getName());
        }
        return value;
    }

    private Object propertyOrNull(Object target, String name) {
        if (target == null) {
            return null;
        }

        String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        Object getter = invokeOrNull(target, "get" + capitalized);
        if (getter != null) {
            return getter;
        }
        Object booleanGetter = invokeOrNull(target, "is" + capitalized);
        if (booleanGetter != null) {
            return booleanGetter;
        }

        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private Object invokeOrNull(Object target, String name) {
        return invokeOrNull(target, name, new Class<?>[0]);
    }

    private Object invokeOrNull(Object target, String name, Class<?>[] parameterTypes, Object... arguments) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(name, parameterTypes);
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
