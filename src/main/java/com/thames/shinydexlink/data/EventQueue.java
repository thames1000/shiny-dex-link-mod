package com.thames.shinydexlink.data;

import com.google.gson.reflect.TypeToken;
import com.thames.shinydexlink.api.dto.CatchEventRequest;
import com.thames.shinydexlink.util.JsonUtil;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EventQueue {
    private static final Type LIST_TYPE = new TypeToken<List<QueuedEvent>>() {
    }.getType();

    private final Path path;
    private final Map<String, QueuedEvent> events = new LinkedHashMap<>();

    public EventQueue(Path path) {
        this.path = path;
    }

    public synchronized void load() throws IOException {
        events.clear();
        if (Files.notExists(path)) {
            save();
            return;
        }

        List<QueuedEvent> loaded = JsonUtil.read(path, LIST_TYPE);
        if (loaded != null) {
            for (QueuedEvent queuedEvent : loaded) {
                if (queuedEvent != null && queuedEvent.event != null && queuedEvent.event.eventId != null) {
                    events.putIfAbsent(queuedEvent.event.eventId, queuedEvent);
                }
            }
        }
    }

    public synchronized void save() throws IOException {
        JsonUtil.writeAtomic(path, new ArrayList<>(events.values()));
    }

    public synchronized boolean enqueueIfAbsent(CatchEventRequest event, String lastError) throws IOException {
        if (event == null || event.eventId == null || events.containsKey(event.eventId)) {
            return false;
        }
        events.put(event.eventId, new QueuedEvent(event, Instant.now(), 0, lastError));
        save();
        return true;
    }

    public synchronized void remove(String eventId) throws IOException {
        if (events.remove(eventId) != null) {
            save();
        }
    }

    public synchronized void markAttempt(String eventId, String lastError) throws IOException {
        QueuedEvent event = events.get(eventId);
        if (event == null) {
            return;
        }
        event.attempts++;
        event.lastError = lastError;
        save();
    }

    public synchronized List<QueuedEvent> snapshot() {
        return new ArrayList<>(events.values());
    }

    public synchronized int size() {
        return events.size();
    }

    public synchronized int countFor(UUID playerUuid) {
        String uuid = playerUuid.toString();
        int count = 0;
        for (QueuedEvent event : events.values()) {
            if (event.event != null && uuid.equals(event.event.minecraftUuid)) {
                count++;
            }
        }
        return count;
    }

    public Path path() {
        return path;
    }
}
