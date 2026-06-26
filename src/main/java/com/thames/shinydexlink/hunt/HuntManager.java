package com.thames.shinydexlink.hunt;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.thames.shinydexlink.util.JsonUtil;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/**
 * Server-authoritative store of every player's active hunts. A player may run several hunts at
 * once (up to {@link #maxHunts}); each is a {@link HuntState} identified within the player's set by
 * {@link HuntState#key()}. All mutation happens on the server thread (commands, Cobblemon events,
 * networked actions), and each change is persisted to {@code hunts.json} so hunts survive a
 * restart. The client only ever holds a read-only mirror pushed to it via networking.
 */
public final class HuntManager {
    private static final Type LIST_TYPE = new TypeToken<List<HuntState>>() {
    }.getType();

    private final Path path;
    private final Logger logger;
    private final int maxHunts;
    private final Map<String, List<HuntState>> hunts = new ConcurrentHashMap<>();

    public HuntManager(Path path, Logger logger, int maxHunts) {
        this.path = path;
        this.logger = logger;
        this.maxHunts = Math.max(1, maxHunts);
    }

    public int maxHunts() {
        return maxHunts;
    }

    public synchronized void load() throws IOException {
        hunts.clear();
        if (Files.notExists(path)) {
            save();
            return;
        }
        JsonObject root = JsonUtil.read(path, JsonObject.class);
        if (root == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            List<HuntState> list = parseEntry(entry.getValue());
            if (!list.isEmpty()) {
                hunts.put(entry.getKey(), list);
            }
        }
    }

    /**
     * Reads one player's hunts from JSON, tolerating both the current list form and the original
     * single-object form (a hunt counter that predates multi-hunt support) so existing files keep
     * working. Duplicate keys are collapsed and the list is trimmed to {@link #maxHunts}.
     */
    private List<HuntState> parseEntry(JsonElement value) {
        List<HuntState> parsed = new ArrayList<>();
        if (value == null || value.isJsonNull()) {
            return parsed;
        }
        if (value.isJsonArray()) {
            List<HuntState> list = JsonUtil.GSON.fromJson(value, LIST_TYPE);
            if (list != null) {
                parsed.addAll(list);
            }
        } else if (value.isJsonObject()) {
            HuntState single = JsonUtil.GSON.fromJson(value, HuntState.class);
            if (single != null) {
                parsed.add(single);
            }
        }
        List<HuntState> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (HuntState state : parsed) {
            if (state == null || state.species == null || !seen.add(state.key())) {
                continue;
            }
            result.add(state);
            if (result.size() >= maxHunts) {
                break;
            }
        }
        return result;
    }

    public synchronized void save() {
        try {
            JsonUtil.writeAtomic(path, hunts);
        } catch (IOException exception) {
            logger.warn("Failed to persist ShinyDex hunts", exception);
        }
    }

    /** All of a player's active hunts, newest last. Never null; the returned list is a snapshot. */
    public List<HuntState> getAll(UUID uuid) {
        List<HuntState> list = hunts.get(uuid.toString());
        return list == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(list));
    }

    public int size(UUID uuid) {
        List<HuntState> list = hunts.get(uuid.toString());
        return list == null ? 0 : list.size();
    }

    public Optional<HuntState> find(UUID uuid, String key) {
        List<HuntState> list = hunts.get(uuid.toString());
        if (list == null || key == null) {
            return Optional.empty();
        }
        synchronized (list) {
            for (HuntState state : list) {
                if (state.key().equals(key)) {
                    return Optional.of(state);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Starts hunting a species/form, or resets the matching hunt to zero if one is already running.
     * Returns null when the species is blank or the player is already at {@link #maxHunts} distinct
     * hunts and this would add a new one.
     */
    public HuntState setTarget(UUID uuid, String species, String displayName, String form,
            boolean countEncounters, boolean countEggs) {
        String normalizedSpecies = species == null ? null : species.toLowerCase(Locale.ROOT).trim();
        if (normalizedSpecies == null || normalizedSpecies.isBlank()) {
            return null;
        }
        String normalizedForm = form == null || form.isBlank() ? null : form.toLowerCase(Locale.ROOT).trim();
        String pretty = displayName == null || displayName.isBlank() ? capitalize(normalizedSpecies) : displayName;
        String key = HuntState.makeKey(normalizedSpecies, normalizedForm);

        List<HuntState> list = hunts.computeIfAbsent(uuid.toString(), ignored -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (list) {
            HuntState fresh = new HuntState(normalizedSpecies, pretty, normalizedForm, countEncounters, countEggs);
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).key().equals(key)) {
                    list.set(i, fresh);
                    save();
                    return fresh;
                }
            }
            if (list.size() >= maxHunts) {
                return null;
            }
            list.add(fresh);
            save();
            return fresh;
        }
    }

    /** Stops a single hunt; returns true if one was removed. */
    public boolean stop(UUID uuid, String key) {
        List<HuntState> list = hunts.get(uuid.toString());
        if (list == null) {
            return false;
        }
        boolean removed;
        synchronized (list) {
            removed = list.removeIf(state -> state.key().equals(key));
            if (list.isEmpty()) {
                hunts.remove(uuid.toString());
            }
        }
        if (removed) {
            save();
        }
        return removed;
    }

    /** Stops every hunt for the player; returns how many were removed. */
    public int stopAll(UUID uuid) {
        List<HuntState> list = hunts.remove(uuid.toString());
        if (list == null || list.isEmpty()) {
            return 0;
        }
        save();
        return list.size();
    }

    /** Zeroes the tallies of one hunt but keeps hunting the same target. */
    public Optional<HuntState> reset(UUID uuid, String key) {
        return mutate(uuid, key, state -> {
            state.encounters = 0;
            state.eggs = 0;
            state.manual = 0;
        });
    }

    public Optional<HuntState> adjustManual(UUID uuid, String key, int delta) {
        return mutate(uuid, key, state -> state.manual += delta);
    }

    public Optional<HuntState> recordEncounter(UUID uuid, String key) {
        return mutate(uuid, key, state -> {
            if (state.countEncounters) {
                state.encounters++;
            }
        });
    }

    public Optional<HuntState> recordEgg(UUID uuid, String key) {
        return mutate(uuid, key, state -> {
            if (state.countEggs) {
                state.eggs++;
            }
        });
    }

    public Optional<HuntState> toggleEncounters(UUID uuid, String key) {
        return mutate(uuid, key, state -> state.countEncounters = !state.countEncounters);
    }

    public Optional<HuntState> toggleEggs(UUID uuid, String key) {
        return mutate(uuid, key, state -> state.countEggs = !state.countEggs);
    }

    /**
     * Seeds a hunt's tallies from progress saved on the website (used when a hunt starts and the
     * site has prior progress for it). Tallies are clamped non-negative; a non-null {@code startedAt}
     * restores the original start time so elapsed-time stats stay continuous.
     */
    public Optional<HuntState> seedFromRemote(UUID uuid, String key, int encounters, int eggs, int manual,
            boolean countEncounters, boolean countEggs, Instant startedAt) {
        return mutate(uuid, key, state -> {
            state.encounters = Math.max(0, encounters);
            state.eggs = Math.max(0, eggs);
            state.manual = manual;
            state.countEncounters = countEncounters;
            state.countEggs = countEggs;
            if (startedAt != null) {
                state.startedAt = startedAt;
            }
        });
    }

    private Optional<HuntState> mutate(UUID uuid, String key, java.util.function.Consumer<HuntState> change) {
        List<HuntState> list = hunts.get(uuid.toString());
        if (list == null || key == null) {
            return Optional.empty();
        }
        synchronized (list) {
            for (HuntState state : list) {
                if (state.key().equals(key)) {
                    change.accept(state);
                    state.updatedAt = Instant.now();
                    save();
                    return Optional.of(state);
                }
            }
        }
        return Optional.empty();
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
