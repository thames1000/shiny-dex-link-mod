package com.thames.shinydexlink.client;

import com.thames.shinydexlink.net.HuntUpdatePayload;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only client mirror of the server's hunts, fed by {@link HuntUpdatePayload}. The overlay and
 * hunt screen read the {@link #hunts()} snapshot from here. Overlay visibility and placement are
 * local-only and live in {@link ClientOverlayConfig}.
 */
public final class ClientHuntState {
    /** One hunt as the client sees it. {@link #key()} matches the server's {@code HuntState.key()}. */
    public record HuntView(
            String species,
            String displayName,
            String form,
            int total,
            int encounters,
            int eggs,
            boolean countEncounters,
            boolean countEggs
    ) {
        public String key() {
            String s = species == null ? "" : species;
            String f = form == null ? "" : form;
            return s + "|" + f;
        }

        public boolean hasForm() {
            return form != null && !form.isBlank();
        }
    }

    private static volatile List<HuntView> hunts = List.of();

    private ClientHuntState() {
    }

    public static void update(HuntUpdatePayload payload) {
        List<HuntView> next = new ArrayList<>();
        for (HuntUpdatePayload.Entry entry : payload.hunts()) {
            next.add(new HuntView(
                    entry.species(),
                    entry.displayName() == null ? "" : entry.displayName(),
                    entry.form() == null ? "" : entry.form(),
                    entry.total(),
                    entry.encounters(),
                    entry.eggs(),
                    entry.countEncounters(),
                    entry.countEggs()));
        }
        hunts = List.copyOf(next);
    }

    public static List<HuntView> hunts() {
        return hunts;
    }

    public static boolean isActive() {
        return !hunts.isEmpty();
    }

    public static boolean overlayVisible() {
        return ClientOverlayConfig.visible();
    }

    public static void toggleOverlay() {
        ClientOverlayConfig.toggleVisible();
    }
}
