package com.thames.shinydexlink.data;

import com.google.gson.reflect.TypeToken;
import com.thames.shinydexlink.util.JsonUtil;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class LinkedPlayerStore {
    private static final Type MAP_TYPE = new TypeToken<Map<String, LinkedPlayer>>() {
    }.getType();

    private final Path path;
    private final Map<String, LinkedPlayer> players = new LinkedHashMap<>();

    public LinkedPlayerStore(Path path) {
        this.path = path;
    }

    public synchronized void load() throws IOException {
        players.clear();
        if (Files.notExists(path)) {
            save();
            return;
        }

        Map<String, LinkedPlayer> loaded = JsonUtil.read(path, MAP_TYPE);
        if (loaded != null) {
            loaded.forEach((uuid, player) -> {
                if (player != null) {
                    players.put(uuid, player);
                }
            });
        }
    }

    public synchronized void save() throws IOException {
        JsonUtil.writeAtomic(path, players);
    }

    public synchronized boolean isLinked(UUID uuid) {
        LinkedPlayer player = players.get(uuid.toString());
        return player != null && player.linked;
    }

    public synchronized Optional<LinkedPlayer> get(UUID uuid) {
        return Optional.ofNullable(players.get(uuid.toString()));
    }

    public synchronized void link(UUID uuid, String minecraftName, String linkedAccountId) throws IOException {
        players.put(uuid.toString(), new LinkedPlayer(minecraftName, true, Instant.now(), null, linkedAccountId));
        save();
    }

    public synchronized void unlink(UUID uuid) throws IOException {
        LinkedPlayer player = players.get(uuid.toString());
        if (player == null) {
            return;
        }
        player.linked = false;
        save();
    }

    public synchronized void updateLastSync(UUID uuid, Instant instant) throws IOException {
        LinkedPlayer player = players.get(uuid.toString());
        if (player == null) {
            return;
        }
        player.lastSyncAt = instant;
        save();
    }

    public synchronized Map<String, LinkedPlayer> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(players));
    }

    public Path path() {
        return path;
    }
}
