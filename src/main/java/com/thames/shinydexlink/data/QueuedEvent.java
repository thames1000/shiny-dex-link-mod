package com.thames.shinydexlink.data;

import com.thames.shinydexlink.api.dto.CatchEventRequest;
import java.time.Instant;

public final class QueuedEvent {
    public CatchEventRequest event;
    public Instant queuedAt;
    public int attempts;
    public String lastError;

    public QueuedEvent() {
    }

    public QueuedEvent(CatchEventRequest event, Instant queuedAt, int attempts, String lastError) {
        this.event = event;
        this.queuedAt = queuedAt;
        this.attempts = attempts;
        this.lastError = lastError;
    }
}
