package com.thames.shinydexlink.api.dto;

/**
 * One hunt's progress as exchanged with the website: pushed when a player disconnects so the site
 * persists it, and returned when a hunt starts so the count can resume. {@code form} is null/blank
 * for an any-form hunt; {@code total} is encounters + eggs + manual (never negative).
 */
public final class HuntProgressDto {
    public String species;
    public String form;
    public String displayName;
    public int encounters;
    public int eggs;
    public int manual;
    public int total;
    public boolean countEncounters = true;
    public boolean countEggs = true;
    public String startedAt;
    public String updatedAt;
}
