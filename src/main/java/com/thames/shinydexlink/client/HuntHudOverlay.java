package com.thames.shinydexlink.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Top-left HUD panel showing the active hunt target and counter. Hidden when there is no active
 * hunt, when the player has toggled the overlay off, or when the vanilla HUD is hidden (F1).
 */
public final class HuntHudOverlay {
    private static final int MARGIN = 4;
    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 11;
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
        if (!ClientHuntState.isActive() || !ClientHuntState.overlayVisible()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.font == null) {
            return;
        }
        Font font = minecraft.font;

        String form = ClientHuntState.form();
        String title = "Hunting " + ClientHuntState.displayName()
                + (form == null || form.isBlank() ? "" : " (" + form + ")");
        String count = "Count: " + ClientHuntState.total();
        String detail = "Enc " + ClientHuntState.encounters() + "   Eggs " + ClientHuntState.eggs();

        int textWidth = Math.max(font.width(title), Math.max(font.width(count), font.width(detail)));
        int boxWidth = textWidth + PADDING * 2;
        int boxHeight = LINE_HEIGHT * 3 + PADDING * 2;

        graphics.fill(MARGIN, MARGIN, MARGIN + boxWidth, MARGIN + boxHeight, BACKGROUND);

        int textX = MARGIN + PADDING;
        int textY = MARGIN + PADDING;
        graphics.drawString(font, title, textX, textY, TITLE_COLOR);
        graphics.drawString(font, count, textX, textY + LINE_HEIGHT, COUNT_COLOR);
        graphics.drawString(font, detail, textX, textY + LINE_HEIGHT * 2, DETAIL_COLOR);
    }
}
