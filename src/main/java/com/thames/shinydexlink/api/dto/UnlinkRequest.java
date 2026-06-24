package com.thames.shinydexlink.api.dto;

public final class UnlinkRequest {
    public transient String serverToken;
    public String serverId;
    public String minecraftUuid;

    public UnlinkRequest(String serverId, String minecraftUuid) {
        this.serverId = serverId;
        this.minecraftUuid = minecraftUuid;
    }
}
