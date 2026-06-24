package com.thames.shinydexlink.api.dto;

import java.util.List;

public final class BerryReportRequest {
    public transient String serverToken;
    public String serverId;
    public String minecraftUuid;
    public String minecraftName;
    public List<String> berries;

    public BerryReportRequest(String serverId, String minecraftUuid, String minecraftName, List<String> berries) {
        this.serverId = serverId;
        this.minecraftUuid = minecraftUuid;
        this.minecraftName = minecraftName;
        this.berries = berries;
    }
}
