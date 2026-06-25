package com.thames.shinydexlink.api.dto;

import java.util.List;

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
    /**
     * Cobblemon aspects for the captured Pokemon (e.g. {@code ["alolan"]},
     * {@code ["region-bias-alola"]}). The Shiny Dex website matches these against
     * its Variants tab catalog (national dex + aspect/form name). Null/omitted for
     * a plain form, so normal mons stay clean in logs and queue files.
     */
    public List<String> aspects;
    public String gender;
    public Integer level;
    public String ball;
    public String caughtAt;
}
