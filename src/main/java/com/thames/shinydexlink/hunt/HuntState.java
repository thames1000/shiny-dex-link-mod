package com.thames.shinydexlink.hunt;

import java.time.Instant;
import java.util.Locale;

/**
 * One player's active shiny hunt: the target species (and optional form), how the counter
 * is allowed to grow automatically, and the running tallies. The total is split across
 * encounters / egg hatches / manual bumps so the website can be told how the shiny was
 * found ({@link #kind()}), and so a player can toggle one source without losing the others.
 */
public final class HuntState {
    /** Normalized species id, e.g. {@code "mareep"}. */
    public String species;
    /** Pretty name for the overlay, e.g. {@code "Mareep"}. */
    public String displayName;
    /** Normalized form/aspect to match, or null to match any form of the species. */
    public String form;

    public int encounters;
    public int eggs;
    public int manual;

    public boolean countEncounters = true;
    public boolean countEggs = true;

    public Instant startedAt;
    public Instant updatedAt;

    public HuntState() {
    }

    public HuntState(String species, String displayName, String form, boolean countEncounters, boolean countEggs) {
        this.species = species;
        this.displayName = displayName;
        this.form = form;
        this.countEncounters = countEncounters;
        this.countEggs = countEggs;
        this.startedAt = Instant.now();
        this.updatedAt = this.startedAt;
    }

    /** Total attempts; never negative even after manual decrements. */
    public int total() {
        return Math.max(0, encounters + eggs + manual);
    }

    /** How the shiny was found, for the website: {@code encounters}, {@code eggs}, or {@code mixed}. */
    public String kind() {
        if (eggs > 0 && encounters > 0) {
            return "mixed";
        }
        if (eggs > 0) {
            return "eggs";
        }
        return "encounters";
    }

    /**
     * True when a caught/hatched/encountered Pokemon counts toward this hunt. The species
     * must match; the form must match only when the hunt pinned a specific form.
     */
    public boolean matches(String otherSpecies, String otherForm) {
        if (species == null || otherSpecies == null) {
            return false;
        }
        if (!species.equals(otherSpecies.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (form == null || form.isBlank()) {
            return true;
        }
        return otherForm != null && form.equals(otherForm.toLowerCase(Locale.ROOT));
    }
}
