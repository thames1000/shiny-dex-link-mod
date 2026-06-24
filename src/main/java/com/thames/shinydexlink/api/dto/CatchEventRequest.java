package com.thames.shinydexlink.api.dto;

public final class CatchEventRequest {
    public transient String serverToken;
    public String eventId;
    public String serverId;
    public String minecraftUuid;
    public String minecraftName;
    public String eventType = "pokemon_caught";
    public String species;
    public String displayName;
    public boolean shiny;
    public String form;
    public String gender;
    public Integer level;
    public String ball;
    public String caughtAt;
}
