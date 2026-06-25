package com.thames.shinydexlink.client;

import com.thames.shinydexlink.cobblemon.SpeciesLookup;
import com.thames.shinydexlink.net.HuntActionPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Hunt control screen (default key: H). Type a species (with live suggestions from Cobblemon's
 * registry), start/stop/reset the hunt, and toggle whether encounters and egg hatches auto-count.
 * Every action is sent to the server, which owns the authoritative hunt state; the live preview
 * reflects {@link ClientHuntState} as updates stream back.
 */
public final class HuntScreen extends Screen {
    private static final int BOX_WIDTH = 200;
    private static final int ROW_HEIGHT = 12;
    private static final int MAX_SUGGESTIONS = 6;

    private EditBox speciesBox;
    private Button encountersButton;
    private Button eggsButton;
    private final List<String> suggestions = new ArrayList<>();
    private int suggestionsX;
    private int suggestionsTop;

    public HuntScreen() {
        super(Component.literal("ShinyDex Hunt"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int boxX = cx - BOX_WIDTH / 2;
        int y = this.height / 2 - 60;

        speciesBox = new EditBox(this.font, boxX, y, BOX_WIDTH, 20, Component.literal("Species"));
        speciesBox.setMaxLength(64);
        speciesBox.setHint(Component.literal("species id, e.g. mareep"));
        speciesBox.setResponder(this::onTextChanged);
        addRenderableWidget(speciesBox);
        setInitialFocus(speciesBox);

        suggestionsX = boxX;
        suggestionsTop = y + 24;

        int buttonsY = suggestionsTop + ROW_HEIGHT * MAX_SUGGESTIONS + 6;
        addRenderableWidget(Button.builder(Component.literal("Start hunt"), button -> startHunt())
                .bounds(boxX, buttonsY, BOX_WIDTH, 20).build());

        int half = BOX_WIDTH / 2 - 2;
        addRenderableWidget(Button.builder(Component.literal("Stop"), button -> {
            send(HuntActionPayload.of(HuntActionPayload.ACTION_STOP));
        }).bounds(boxX, buttonsY + 24, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
            send(HuntActionPayload.of(HuntActionPayload.ACTION_RESET));
        }).bounds(boxX + half + 4, buttonsY + 24, half, 20).build());

        encountersButton = addRenderableWidget(Button.builder(encountersLabel(), button -> {
            send(HuntActionPayload.of(HuntActionPayload.ACTION_TOGGLE_ENCOUNTERS));
        }).bounds(boxX, buttonsY + 48, half, 20).build());
        eggsButton = addRenderableWidget(Button.builder(eggsLabel(), button -> {
            send(HuntActionPayload.of(HuntActionPayload.ACTION_TOGGLE_EGGS));
        }).bounds(boxX + half + 4, buttonsY + 48, half, 20).build());
    }

    private void startHunt() {
        String species = speciesBox.getValue().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (!species.isBlank()) {
            send(new HuntActionPayload(HuntActionPayload.ACTION_SET_TARGET, species));
            suggestions.clear();
        }
    }

    private void onTextChanged(String text) {
        suggestions.clear();
        String query = text.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return;
        }
        for (String id : SpeciesLookup.allIds()) {
            if (id.startsWith(query)) {
                suggestions.add(id);
                if (suggestions.size() >= MAX_SUGGESTIONS) {
                    break;
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        encountersButton.setMessage(encountersLabel());
        eggsButton.setMessage(eggsLabel());

        int cx = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, cx, this.height / 2 - 88, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal(previewLine()), cx, this.height / 2 - 76, 0xFFA0E0A0);

        for (int i = 0; i < suggestions.size(); i++) {
            int rowY = suggestionsTop + i * ROW_HEIGHT;
            boolean hovered = mouseX >= suggestionsX && mouseX <= suggestionsX + BOX_WIDTH
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hovered) {
                graphics.fill(suggestionsX, rowY, suggestionsX + BOX_WIDTH, rowY + ROW_HEIGHT, 0x40FFFFFF);
            }
            graphics.drawString(this.font, suggestions.get(i), suggestionsX + 2, rowY + 2,
                    hovered ? 0xFFFFFF00 : 0xFFC0C0C0);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < suggestions.size(); i++) {
            int rowY = suggestionsTop + i * ROW_HEIGHT;
            if (mouseX >= suggestionsX && mouseX <= suggestionsX + BOX_WIDTH
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                speciesBox.setValue(suggestions.get(i));
                suggestions.clear();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private String previewLine() {
        if (!ClientHuntState.isActive()) {
            return "No active hunt";
        }
        int manual = ClientHuntState.total() - ClientHuntState.encounters() - ClientHuntState.eggs();
        return "Hunting " + ClientHuntState.displayName() + " - " + ClientHuntState.total()
                + " (enc " + ClientHuntState.encounters() + ", eggs " + ClientHuntState.eggs()
                + ", manual " + manual + ")";
    }

    private Component encountersLabel() {
        return Component.literal("Encounters: " + (ClientHuntState.countEncounters() ? "ON" : "OFF"));
    }

    private Component eggsLabel() {
        return Component.literal("Eggs: " + (ClientHuntState.countEggs() ? "ON" : "OFF"));
    }

    private void send(HuntActionPayload payload) {
        HuntKeybinds.send(payload);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
