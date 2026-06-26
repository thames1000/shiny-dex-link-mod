package com.thames.shinydexlink.api.dto;

import java.util.List;

/**
 * Push of a player's full set of active hunts, sent when they disconnect. The list is a complete
 * snapshot: the backend should replace whatever it had stored for this player with {@link #hunts}
 * (an empty list clears their stored progress). {@code serverToken} is added by the API client.
 */
public final class HuntSyncRequest {
    public String serverId;
    public String minecraftUuid;
    public String minecraftName;
    public List<HuntProgressDto> hunts;
    public String reportedAt;

    public HuntSyncRequest() {
    }

    public HuntSyncRequest(String serverId, String minecraftUuid, String minecraftName,
            List<HuntProgressDto> hunts, String reportedAt) {
        this.serverId = serverId;
        this.minecraftUuid = minecraftUuid;
        this.minecraftName = minecraftName;
        this.hunts = hunts;
        this.reportedAt = reportedAt;
    }
}
