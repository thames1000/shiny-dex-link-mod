package com.thames.shinydexlink.api.dto;

import java.util.List;

/**
 * Asks the website to clear the shiny-caught state for a species/variant the player no longer owns.
 * Sent when a shiny evolves and no other shiny of the pre-evolution species remains in the player's
 * party or PC, so the dex reflects currently-owned shinies rather than ever-owned ones.
 */
public final class ShinyRemovalRequest {
    public transient String serverToken;
    public String serverId;
    public String minecraftUuid;
    public String minecraftName;
    public String species;
    public String form;
    public List<String> aspects;
    /** Why the shiny was removed, e.g. {@code "evolved"}. Lets the backend log/audit the change. */
    public String reason;
    public String removedAt;
}
