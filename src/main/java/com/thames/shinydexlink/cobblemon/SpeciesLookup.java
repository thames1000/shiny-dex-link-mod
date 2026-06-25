package com.thames.shinydexlink.cobblemon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads Cobblemon's species registry via reflection so the mod can offer species suggestions
 * (client) and resolve a pretty display name for a species id (server) without a compile-time
 * Cobblemon dependency. Loaded lazily and cached; degrades to a capitalized id if Cobblemon
 * isn't present or its API moved.
 *
 * Verified against Cobblemon 1.7.3 ({@code PokemonSpecies.INSTANCE.getSpecies()}).
 */
public final class SpeciesLookup {
    private static volatile Map<String, String> idToDisplay;

    private SpeciesLookup() {
    }

    /** Sorted list of normalized species ids, or empty if Cobblemon's registry is unavailable. */
    public static List<String> allIds() {
        return new ArrayList<>(catalog().keySet());
    }

    /** Pretty display name for a species id, falling back to a capitalized id. */
    public static String displayName(String speciesId) {
        if (speciesId == null || speciesId.isBlank()) {
            return "";
        }
        String id = speciesId.toLowerCase(Locale.ROOT).trim();
        String display = catalog().get(id);
        return display != null ? display : capitalize(id);
    }

    private static Map<String, String> catalog() {
        Map<String, String> cached = idToDisplay;
        if (cached != null) {
            return cached;
        }
        Map<String, String> loaded = load();
        idToDisplay = loaded;
        return loaded;
    }

    private static Map<String, String> load() {
        Map<String, String> result = new TreeMap<>();
        try {
            Class<?> speciesClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonSpecies");
            Object instance = speciesClass.getField("INSTANCE").get(null);
            Object species = CobblemonReflect.invokeOrNull(instance, "getSpecies");
            if (species instanceof Collection<?> collection) {
                for (Object entry : collection) {
                    String id = idOf(entry);
                    if (id == null) {
                        continue;
                    }
                    Object name = CobblemonReflect.propertyOrNull(entry, "name");
                    result.put(id, name == null ? capitalize(id) : name.toString());
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Cobblemon absent or API changed: suggestions/display fall back to capitalized ids.
        }
        return result;
    }

    private static String idOf(Object species) {
        Object showdownId = CobblemonReflect.invokeOrNull(species, "showdownId");
        if (showdownId != null && !showdownId.toString().isBlank()) {
            return showdownId.toString().toLowerCase(Locale.ROOT);
        }
        Object identifier = CobblemonReflect.propertyOrNull(species, "resourceIdentifier");
        Object path = CobblemonReflect.invokeOrNull(identifier, "getPath");
        return path == null ? null : path.toString().toLowerCase(Locale.ROOT);
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
