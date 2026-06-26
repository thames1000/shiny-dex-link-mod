package com.thames.shinydexlink.api.dto;

/**
 * Asks the backend for any saved progress for one hunt (a player + species, optionally a form),
 * sent when a hunt starts so it can resume. {@code serverToken} is added by the API client.
 */
public final class HuntFetchRequest {
    public String serverId;
    public String minecraftUuid;
    public String minecraftName;
    public String species;
    public String form;

    public HuntFetchRequest() {
    }

    public HuntFetchRequest(String serverId, String minecraftUuid, String minecraftName, String species, String form) {
        this.serverId = serverId;
        this.minecraftUuid = minecraftUuid;
        this.minecraftName = minecraftName;
        this.species = species;
        this.form = form;
    }
}
