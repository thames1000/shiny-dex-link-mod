package com.thames.shinydexlink.client;

import com.thames.shinydexlink.client.ClientHuntState.HuntView;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD panel listing every active hunt and its counter. The player drags it anywhere via
 * {@link OverlayEditScreen}; the chosen spot is saved in {@link ClientOverlayConfig}. Until it has
 * been placed it sits in the top-right corner, clear of the battle info Cobblemon draws top-left.
 * Hidden when there are no hunts, when the overlay is toggled off, or when the vanilla HUD is (F1).
 */
public final class HuntHudOverlay {
    static final int MARGIN = 4;
    static final int PADDING = 4;
    static final int LINE_HEIGHT = 11;
    private static final int GAP = 8;
    private static final int BACKGROUND = 0x90000000;
    private static final int TITLE_COLOR = 0xFFFFE066;
    private static final int COUNT_COLOR = 0xFFFFFFFF;
    private static final int DETAIL_COLOR = 0xFFB0B0B0;

    private HuntHudOverlay() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> render(graphics));
    }

    private static void render(GuiGraphics graphics) {
        if (!ClientHuntState.isActive() || !ClientOverlayConfig.visible()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.font == null) {
            return;
        }
        List<HuntView> hunts = ClientHuntState.hunts();
        int[] size = measure(minecraft.font, hunts);
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        int x;
        int y;
        if (ClientOverlayConfig.placed()) {
            x = ClientOverlayConfig.x();
            y = ClientOverlayConfig.y();
        } else {
            x = screenWidth - size[0] - MARGIN;
            y = MARGIN;
        }
        x = clamp(x, 0, Math.max(0, screenWidth - size[0]));
        y = clamp(y, 0, Math.max(0, screenHeight - size[1]));

        drawAt(graphics, minecraft.font, x, y, hunts);
    }

    /** Box width/height for a set of hunts, so callers can position or clamp before drawing. */
    static int[] measure(Font font, List<HuntView> hunts) {
        int textWidth = 0;
        for (HuntView hunt : hunts) {
            textWidth = Math.max(textWidth, font.width(titleOf(hunt)) + GAP + font.width(statsOf(hunt)));
        }
        int rows = Math.max(1, hunts.size());
        return new int[] {textWidth + PADDING * 2, LINE_HEIGHT * rows + PADDING * 2};
    }

    /** Draws the overlay's top-left corner at {@code (x, y)} and returns its measured size. */
    static int[] drawAt(GuiGraphics graphics, Font font, int x, int y, List<HuntView> hunts) {
        int[] size = measure(font, hunts);
        graphics.fill(x, y, x + size[0], y + size[1], BACKGROUND);

        int textX = x + PADDING;
        int textY = y + PADDING;
        for (HuntView hunt : hunts) {
            String title = titleOf(hunt);
            graphics.drawString(font, title, textX, textY, TITLE_COLOR);
            graphics.drawString(font, statsOf(hunt), textX + font.width(title) + GAP, textY, COUNT_COLOR);
            textY += LINE_HEIGHT;
        }
        if (hunts.isEmpty()) {
            graphics.drawString(font, "Shiny hunt overlay", textX, textY, DETAIL_COLOR);
        }
        return size;
    }

    private static String titleOf(HuntView hunt) {
        return hunt.hasForm() ? hunt.displayName() + " (" + hunt.form() + ")" : hunt.displayName();
    }

    private static String statsOf(HuntView hunt) {
        return hunt.total() + "  (E" + hunt.encounters() + " G" + hunt.eggs() + ")";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
