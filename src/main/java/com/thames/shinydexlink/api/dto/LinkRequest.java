package com.thames.shinydexlink.api.dto;

public final class LinkRequest {
    public transient String serverToken;
    public String serverId;
    public String linkCode;
    public String minecraftUuid;
    public String minecraftName;

    public LinkRequest(String serverId, String linkCode, String minecraftUuid, String minecraftName) {
        this.serverId = serverId;
        this.linkCode = linkCode;
        this.minecraftUuid = minecraftUuid;
        this.minecraftName = minecraftName;
    }
}
