package com.thames.shinydexlink.data;

import java.time.Instant;

public final class LinkedPlayer {
    public String minecraftName;
    public boolean linked;
    public Instant linkedAt;
    public Instant lastSyncAt;
    public String linkedAccountId;

    public LinkedPlayer() {
    }

    public LinkedPlayer(String minecraftName, boolean linked, Instant linkedAt, Instant lastSyncAt, String linkedAccountId) {
        this.minecraftName = minecraftName;
        this.linked = linked;
        this.linkedAt = linkedAt;
        this.lastSyncAt = lastSyncAt;
        this.linkedAccountId = linkedAccountId;
    }
}
