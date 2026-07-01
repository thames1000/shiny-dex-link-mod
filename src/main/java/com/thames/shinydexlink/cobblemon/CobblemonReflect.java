package com.thames.shinydexlink.cobblemon;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared reflection helpers for reading Cobblemon API objects without a compile-time
 * dependency. Keeping these in one place lets every Cobblemon hook (captures, battles,
 * egg hatches) read {@code Pokemon} fields the same way, so a Cobblemon API change only
 * needs fixing here.
 *
 * Verified against Cobblemon 1.7.3.
 */
public final class CobblemonReflect {
    private CobblemonReflect() {
    }

    /** Normalized showdown/species id, e.g. {@code "mareep"}. */
    public static String speciesId(Object pokemon) {
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

    public static String displayName(Object pokemon) {
        Object component = invokeOrNull(pokemon, "getDisplayName", new Class<?>[]{boolean.class}, false);
        if (component instanceof Component minecraftComponent) {
            return minecraftComponent.getString();
        }

        Object species = propertyOrNull(pokemon, "species");
        Object name = propertyOrNull(species, "name");
        return name == null ? "Unknown" : name.toString();
    }

    public static String formName(Object pokemon) {
        Object form = propertyOrNull(pokemon, "form");
        Object formName = propertyOrNull(form, "name");
        return formName == null ? "normal" : formName.toString();
    }

    /**
     * Cobblemon {@code Pokemon.getAspects()} returns the aspect tags that identify a
     * form/variant (e.g. {@code alolan}, {@code region-bias-alola}). The website keys its
     * Variants tab on these, so we forward them as a normalized list and let the backend
     * resolve the variant id. Returns null when there's nothing a variant could match on,
     * so plain mons don't add noise to payloads/queue files.
     */
    public static List<String> aspects(Object pokemon) {
        Object value = propertyOrNull(pokemon, "aspects");
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            return null;
        }

        List<String> result = new ArrayList<>();
        for (Object aspect : collection) {
            if (aspect == null) {
                continue;
            }
            // "shiny" is already carried by the dedicated shiny flag; skip it so the
            // backend's aspect->variant lookup only sees form-identifying tags.
            String normalized = aspect.toString().toLowerCase(Locale.ROOT).trim();
            if (!normalized.isEmpty() && !normalized.equals("shiny") && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result.isEmpty() ? null : result;
    }

    public static String ballName(Object pokeBallEntity, Object pokemon) {
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

    public static boolean booleanProperty(Object target, String name, boolean fallback) {
        Object value = propertyOrNull(target, name);
        return value instanceof Boolean booleanValue ? booleanValue : fallback;
    }

    public static Integer intProperty(Object target, String name) {
        Object value = propertyOrNull(target, name);
        return value instanceof Number number ? number.intValue() : null;
    }

    public static String enumName(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return value.toString();
    }

    /** Lowercases, replaces spaces with underscores, and strips any {@code namespace:} prefix. */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT).replace(' ', '_');
        int namespace = normalized.indexOf(':');
        return namespace >= 0 ? normalized.substring(namespace + 1) : normalized;
    }

    /**
     * Counts shiny Pokémon of {@code speciesId} across the player's party and PC.
     *
     * <p>Verified against Cobblemon 1.7.3: {@code Cobblemon.INSTANCE.getStorage()} yields a
     * {@code PokemonStoreManager}, whose {@code getParty(ServerPlayer)} / {@code getPC(ServerPlayer)}
     * return stores that are {@code Iterable<Pokemon>}. Called on the server thread from the
     * evolution hook so the stores are safe to read. Returns 0 if Cobblemon or the storage API is
     * unavailable, which the caller treats as "no duplicate found".
     */
    public static int countShiny(net.minecraft.server.level.ServerPlayer player, String speciesId) {
        if (player == null || speciesId == null) {
            return 0;
        }
        int count = 0;
        for (Object store : playerStores(player)) {
            if (!(store instanceof Iterable<?> iterable)) {
                continue;
            }
            for (Object pokemon : iterable) {
                if (pokemon != null && booleanProperty(pokemon, "shiny", false)
                        && speciesId.equals(speciesId(pokemon))) {
                    count++;
                }
            }
        }
        return count;
    }

    private static List<Object> playerStores(net.minecraft.server.level.ServerPlayer player) {
        List<Object> stores = new ArrayList<>();
        try {
            Class<?> cobblemonClass = Class.forName("com.cobblemon.mod.common.Cobblemon");
            Object instance = cobblemonClass.getField("INSTANCE").get(null);
            Object storage = invokeOrNull(instance, "getStorage");
            Class<?>[] playerArg = {net.minecraft.server.level.ServerPlayer.class};
            Object party = invokeOrNull(storage, "getParty", playerArg, player);
            Object pc = invokeOrNull(storage, "getPC", playerArg, player);
            if (party != null) {
                stores.add(party);
            }
            if (pc != null) {
                stores.add(pc);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Cobblemon missing or storage API changed; caller falls back to "no duplicate".
        }
        return stores;
    }

    public static Object property(Object target, String name) {
        Object value = propertyOrNull(target, name);
        if (value == null) {
            throw new IllegalStateException("Missing Cobblemon property " + name + " on " + target.getClass().getName());
        }
        return value;
    }

    public static Object propertyOrNull(Object target, String name) {
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

    public static Object invokeOrNull(Object target, String name) {
        return invokeOrNull(target, name, new Class<?>[0]);
    }

    public static Object invokeOrNull(Object target, String name, Class<?>[] parameterTypes, Object... arguments) {
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
