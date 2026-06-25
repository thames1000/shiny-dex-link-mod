package com.thames.shinydexlink.client;

import com.thames.shinydexlink.net.HuntUpdatePayload;

/**
 * Read-only client mirror of the server's hunt state, fed by {@link HuntUpdatePayload}. The
 * overlay and hunt screen read from here. {@code overlayVisible} is a purely local toggle and
 * is not synced to the server.
 */
public final class ClientHuntState {
    private static volatile boolean active;
    private static volatile String displayName = "";
    private static volatile String form = "";
    private static volatile int total;
    private static volatile int encounters;
    private static volatile int eggs;
    private static volatile boolean countEncounters = true;
    private static volatile boolean countEggs = true;
    private static volatile boolean overlayVisible = true;

    private ClientHuntState() {
    }

    public static void update(HuntUpdatePayload payload) {
        active = payload.active();
        displayName = payload.displayName() == null ? "" : payload.displayName();
        form = payload.form() == null ? "" : payload.form();
        total = payload.total();
        encounters = payload.encounters();
        eggs = payload.eggs();
        countEncounters = payload.countEncounters();
        countEggs = payload.countEggs();
    }

    public static boolean isActive() {
        return active;
    }

    public static String displayName() {
        return displayName;
    }

    public static String form() {
        return form;
    }

    public static int total() {
        return total;
    }

    public static int encounters() {
        return encounters;
    }

    public static int eggs() {
        return eggs;
    }

    public static boolean countEncounters() {
        return countEncounters;
    }

    public static boolean countEggs() {
        return countEggs;
    }

    public static boolean overlayVisible() {
        return overlayVisible;
    }

    public static void toggleOverlay() {
        overlayVisible = !overlayVisible;
    }
}
