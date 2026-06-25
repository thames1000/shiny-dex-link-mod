package com.thames.shinydexlink.hunt;

import com.google.gson.reflect.TypeToken;
import com.thames.shinydexlink.util.JsonUtil;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/**
 * Server-authoritative store of every player's active hunt. All mutation happens on the
 * server thread (commands, Cobblemon events, networked actions), and each change is
 * persisted to {@code hunts.json} so a hunt survives a restart. The client only ever holds
 * a read-only mirror pushed to it via networking.
 */
public final class HuntManager {
    private static final Type MAP_TYPE = new TypeToken<Map<String, HuntState>>() {
    }.getType();

    private final Path path;
    private final Logger logger;
    private final Map<String, HuntState> hunts = new ConcurrentHashMap<>();

    public HuntManager(Path path, Logger logger) {
        this.path = path;
        this.logger = logger;
    }

    public synchronized void load() throws IOException {
        hunts.clear();
        if (Files.notExists(path)) {
            save();
            return;
        }
        Map<String, HuntState> loaded = JsonUtil.read(path, MAP_TYPE);
        if (loaded != null) {
            loaded.forEach((uuid, state) -> {
                if (state != null && state.species != null) {
                    hunts.put(uuid, state);
                }
            });
        }
    }

    public synchronized void save() {
        try {
            JsonUtil.writeAtomic(path, hunts);
        } catch (IOException exception) {
            logger.warn("Failed to persist ShinyDex hunts", exception);
        }
    }

    public Optional<HuntState> get(UUID uuid) {
        return Optional.ofNullable(hunts.get(uuid.toString()));
    }

    public HuntState setTarget(UUID uuid, String species, String displayName, String form,
            boolean countEncounters, boolean countEggs) {
        String normalizedSpecies = species == null ? null : species.toLowerCase(Locale.ROOT).trim();
        if (normalizedSpecies == null || normalizedSpecies.isBlank()) {
            return null;
        }
        String normalizedForm = form == null || form.isBlank() ? null : form.toLowerCase(Locale.ROOT).trim();
        String pretty = displayName == null || displayName.isBlank() ? capitalize(normalizedSpecies) : displayName;
        HuntState state = new HuntState(normalizedSpecies, pretty, normalizedForm, countEncounters, countEggs);
        hunts.put(uuid.toString(), state);
        save();
        return state;
    }

    public void stop(UUID uuid) {
        if (hunts.remove(uuid.toString()) != null) {
            save();
        }
    }

    /** Zeroes the tallies but keeps hunting the same target. */
    public Optional<HuntState> reset(UUID uuid) {
        return mutate(uuid, state -> {
            state.encounters = 0;
            state.eggs = 0;
            state.manual = 0;
        });
    }

    public Optional<HuntState> adjustManual(UUID uuid, int delta) {
        return mutate(uuid, state -> state.manual += delta);
    }

    public Optional<HuntState> recordEncounter(UUID uuid) {
        return mutate(uuid, state -> {
            if (state.countEncounters) {
                state.encounters++;
            }
        });
    }

    public Optional<HuntState> recordEgg(UUID uuid) {
        return mutate(uuid, state -> {
            if (state.countEggs) {
                state.eggs++;
            }
        });
    }

    public Optional<HuntState> toggleEncounters(UUID uuid) {
        return mutate(uuid, state -> state.countEncounters = !state.countEncounters);
    }

    public Optional<HuntState> toggleEggs(UUID uuid) {
        return mutate(uuid, state -> state.countEggs = !state.countEggs);
    }

    private Optional<HuntState> mutate(UUID uuid, java.util.function.Consumer<HuntState> change) {
        HuntState state = hunts.get(uuid.toString());
        if (state == null) {
            return Optional.empty();
        }
        change.accept(state);
        state.updatedAt = Instant.now();
        save();
        return Optional.of(state);
    }

    private static String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    public Path path() {
        return path;
    }
}
