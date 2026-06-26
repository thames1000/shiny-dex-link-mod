package com.thames.shinydexlink.client;

import com.thames.shinydexlink.util.JsonUtil;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Purely client-side, persisted overlay preferences: where the hunt HUD sits and whether it is
 * shown. Saved to {@code config/shinydex-link-client.json} so a player's chosen placement survives
 * a restart. None of this is synced to the server — it only affects local rendering.
 *
 * <p>{@code x}/{@code y} default to {@code -1}, meaning "not yet placed"; the overlay then picks a
 * safe top-right starting spot (clear of the battle HP bar) the first time it renders.
 */
public final class ClientOverlayConfig {
    public int x = -1;
    public int y = -1;
    public boolean visible = true;

    private static volatile ClientOverlayConfig instance = new ClientOverlayConfig();
    private static Path path;

    public static void init(Path configDir) {
        path = configDir.resolve("shinydex-link-client.json");
        load();
    }

    private static void load() {
        if (path == null || Files.notExists(path)) {
            return;
        }
        try {
            ClientOverlayConfig loaded = JsonUtil.read(path, ClientOverlayConfig.class);
            if (loaded != null) {
                instance = loaded;
            }
        } catch (Exception exception) {
            // A corrupt local file should never stop the client; fall back to defaults.
        }
    }

    private static void save() {
        if (path == null) {
            return;
        }
        try {
            JsonUtil.writeAtomic(path, instance);
        } catch (Exception exception) {
            // Best-effort; placement is a convenience, not critical state.
        }
    }

    public static boolean placed() {
        return instance.x >= 0 && instance.y >= 0;
    }

    public static int x() {
        return instance.x;
    }

    public static int y() {
        return instance.y;
    }

    public static void setPosition(int x, int y) {
        instance.x = x;
        instance.y = y;
        save();
    }

    public static boolean visible() {
        return instance.visible;
    }

    public static void toggleVisible() {
        instance.visible = !instance.visible;
        save();
    }

    /** Forgets the saved position so the overlay returns to its default corner on next render. */
    public static void resetPosition() {
        instance.x = -1;
        instance.y = -1;
        save();
    }
}
