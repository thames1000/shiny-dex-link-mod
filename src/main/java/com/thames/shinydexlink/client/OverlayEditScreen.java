package com.thames.shinydexlink.client;

import com.thames.shinydexlink.client.ClientHuntState.HuntView;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Drag-to-place editor for the hunt HUD. Renders the overlay (the live hunts, or a small sample if
 * none are active) and lets the player drag it anywhere; the position is clamped to the screen and
 * saved to {@link ClientOverlayConfig} on close. Reached from {@link HuntScreen}.
 */
public final class OverlayEditScreen extends Screen {
    private static final List<HuntView> SAMPLE = List.of(
            new HuntView("mareep", "Mareep", "", 142, 130, 12, true, true),
            new HuntView("gible", "Gible", "", 89, 89, 0, true, true));

    private final Screen parent;
    private int x;
    private int y;
    private int[] panelSize = {0, 0};
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    public OverlayEditScreen(Screen parent) {
        super(Component.literal("Place Hunt Overlay"));
        this.parent = parent;
    }

    private List<HuntView> hunts() {
        List<HuntView> live = ClientHuntState.hunts();
        return live.isEmpty() ? SAMPLE : live;
    }

    @Override
    protected void init() {
        panelSize = HuntHudOverlay.measure(this.font, hunts());
        if (ClientOverlayConfig.placed()) {
            x = ClientOverlayConfig.x();
            y = ClientOverlayConfig.y();
        } else {
            x = this.width - panelSize[0] - HuntHudOverlay.MARGIN;
            y = HuntHudOverlay.MARGIN;
        }
        clampPosition();

        int buttonW = 150;
        int cx = this.width / 2;
        addRenderableWidget(Button.builder(Component.literal("Reset to default"), button -> {
            ClientOverlayConfig.resetPosition();
            x = this.width - panelSize[0] - HuntHudOverlay.MARGIN;
            y = HuntHudOverlay.MARGIN;
            clampPosition();
        }).bounds(cx - buttonW - 4, this.height - 30, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(cx + 4, this.height - 30, buttonW, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, Component.literal("Drag the overlay to place it, then press Done"),
                this.width / 2, 24, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.literal(ClientHuntState.hunts().isEmpty() ? "(showing a sample — no active hunts)" : ""),
                this.width / 2, 38, 0xFFA0A0A0);

        // Outline the draggable area so it is obvious even when the panel is small.
        int border = 0x60FFE066;
        graphics.fill(x - 1, y - 1, x + panelSize[0] + 1, y, border);
        graphics.fill(x - 1, y + panelSize[1], x + panelSize[0] + 1, y + panelSize[1] + 1, border);
        graphics.fill(x - 1, y, x, y + panelSize[1], border);
        graphics.fill(x + panelSize[0], y, x + panelSize[0] + 1, y + panelSize[1], border);

        panelSize = HuntHudOverlay.drawAt(graphics, this.font, x, y, hunts());

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && withinPanel(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = (int) Math.round(mouseX) - x;
            dragOffsetY = (int) Math.round(mouseY) - y;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            x = (int) Math.round(mouseX) - dragOffsetX;
            y = (int) Math.round(mouseY) - dragOffsetY;
            clampPosition();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean withinPanel(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + panelSize[0] && mouseY >= y && mouseY <= y + panelSize[1];
    }

    private void clampPosition() {
        x = Math.max(0, Math.min(x, this.width - panelSize[0]));
        y = Math.max(0, Math.min(y, this.height - panelSize[1]));
    }

    @Override
    public void onClose() {
        ClientOverlayConfig.setPosition(x, y);
        this.minecraft.setScreen(parent);
    }
}
