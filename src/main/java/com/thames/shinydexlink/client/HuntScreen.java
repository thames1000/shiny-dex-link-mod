package com.thames.shinydexlink.client;

import com.thames.shinydexlink.client.ClientHuntState.HuntView;
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
 * Hunt control screen (default key: H). Add a new hunt by species (with live suggestions), and for
 * each active hunt adjust its counter, toggle encounter/egg auto-counting, reset it, or stop it.
 * "Edit overlay position" opens the drag-to-place editor; "Stop all" clears every hunt. Every
 * action goes to the server, which owns the authoritative state; rows refresh as updates stream
 * back into {@link ClientHuntState}.
 */
public final class HuntScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int ROW_HEIGHT = 12;
    private static final int MAX_SUGGESTIONS = 5;
    private static final int HUNT_ROW_HEIGHT = 22;

    // Per-row button widths, laid out right to left.
    private static final int W_STEP = 18;
    private static final int W_TOGGLE = 44;
    private static final int W_RESET = 46;
    private static final int W_STOP = 40;
    private static final int BTN_GAP = 3;

    private EditBox speciesBox;
    private String speciesText = "";
    private final List<String> suggestions = new ArrayList<>();
    private int suggestionsX;
    private int suggestionsTop;
    private int listTop;
    private int panelLeft;

    private List<HuntView> shownHunts = List.of();
    private String renderedSignature = "";

    public HuntScreen() {
        super(Component.literal("ShinyDex Hunts"));
    }

    @Override
    protected void init() {
        int panelW = Math.min(PANEL_WIDTH, this.width - 20);
        panelLeft = (this.width - panelW) / 2;
        int top = 40;

        int addWidth = 86;
        speciesBox = new EditBox(this.font, panelLeft, top, panelW - addWidth - 4, 20, Component.literal("Species"));
        speciesBox.setMaxLength(64);
        speciesBox.setHint(Component.literal("species id, e.g. mareep"));
        speciesBox.setValue(speciesText);
        speciesBox.setResponder(this::onTextChanged);
        addRenderableWidget(speciesBox);

        addRenderableWidget(Button.builder(Component.literal("Add hunt"), button -> startHunt())
                .bounds(panelLeft + panelW - addWidth, top, addWidth, 20).build());

        suggestionsX = panelLeft;
        suggestionsTop = top + 24;

        int actionsY = suggestionsTop + ROW_HEIGHT * MAX_SUGGESTIONS + 6;
        int half = panelW / 2 - 2;
        addRenderableWidget(Button.builder(Component.literal("Edit overlay position"),
                button -> this.minecraft.setScreen(new OverlayEditScreen(this)))
                .bounds(panelLeft, actionsY, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Stop all hunts"),
                button -> send(HuntActionPayload.of(HuntActionPayload.ACTION_STOP_ALL)))
                .bounds(panelLeft + half + 4, actionsY, half, 20).build());

        listTop = actionsY + 30;

        shownHunts = ClientHuntState.hunts();
        renderedSignature = signature(shownHunts);
        buildHuntRows(panelLeft, panelW);
    }

    private void buildHuntRows(int panelLeft, int panelW) {
        int rightEdge = panelLeft + panelW;
        for (int i = 0; i < shownHunts.size(); i++) {
            HuntView hunt = shownHunts.get(i);
            String key = hunt.key();
            int rowY = listTop + i * HUNT_ROW_HEIGHT;

            int totalButtons = W_STEP + W_STEP + W_TOGGLE + W_TOGGLE + W_RESET + W_STOP + BTN_GAP * 5;
            int x = rightEdge - totalButtons;

            x = addSmall(x, rowY, W_STEP, "-", () -> send(new HuntActionPayload(HuntActionPayload.ACTION_DECREMENT, key)));
            x = addSmall(x, rowY, W_STEP, "+", () -> send(new HuntActionPayload(HuntActionPayload.ACTION_INCREMENT, key)));
            x = addSmall(x, rowY, W_TOGGLE, "Enc:" + onOff(hunt.countEncounters()),
                    () -> send(new HuntActionPayload(HuntActionPayload.ACTION_TOGGLE_ENCOUNTERS, key)));
            x = addSmall(x, rowY, W_TOGGLE, "Egg:" + onOff(hunt.countEggs()),
                    () -> send(new HuntActionPayload(HuntActionPayload.ACTION_TOGGLE_EGGS, key)));
            x = addSmall(x, rowY, W_RESET, "Reset", () -> send(new HuntActionPayload(HuntActionPayload.ACTION_RESET, key)));
            addSmall(x, rowY, W_STOP, "Stop", () -> send(new HuntActionPayload(HuntActionPayload.ACTION_STOP, key)));
        }
    }

    private int addSmall(int x, int y, int width, String text, Runnable onPress) {
        addRenderableWidget(Button.builder(Component.literal(text), button -> onPress.run())
                .bounds(x, y, width, 20).build());
        return x + width + BTN_GAP;
    }

    private void startHunt() {
        String raw = speciesBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (raw.isBlank()) {
            return;
        }
        // Allow "species form" — send it as the server's species|form target.
        String[] parts = raw.split("\\s+", 2);
        String arg = parts.length == 2 ? parts[0] + "|" + parts[1] : parts[0].replace(' ', '_');
        send(new HuntActionPayload(HuntActionPayload.ACTION_SET_TARGET, arg));
        speciesBox.setValue("");
        speciesText = "";
        suggestions.clear();
    }

    private void onTextChanged(String text) {
        speciesText = text;
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
        // Rebuild the per-hunt rows when the server pushes a changed snapshot.
        List<HuntView> current = ClientHuntState.hunts();
        if (!signature(current).equals(renderedSignature)) {
            this.rebuildWidgets();
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, cx, 18, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.literal(shownHunts.size() + " active hunt" + (shownHunts.size() == 1 ? "" : "s")),
                cx, 30, 0xFFA0E0A0);

        renderSuggestions(graphics, mouseX, mouseY);

        if (shownHunts.isEmpty()) {
            graphics.drawString(this.font, "No active hunts. Add one above.", panelLeft, listTop + 4, 0xFFB0B0B0);
        }
        for (int i = 0; i < shownHunts.size(); i++) {
            HuntView hunt = shownHunts.get(i);
            int rowY = listTop + i * HUNT_ROW_HEIGHT + 6;
            String name = hunt.hasForm() ? hunt.displayName() + " (" + hunt.form() + ")" : hunt.displayName();
            graphics.drawString(this.font, name, panelLeft, rowY - 4, 0xFFFFE066);
            graphics.drawString(this.font, "x" + hunt.total() + "  E" + hunt.encounters() + " G" + hunt.eggs(),
                    panelLeft, rowY + 5, 0xFFB0B0B0);
        }
    }

    private void renderSuggestions(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < suggestions.size(); i++) {
            int rowY = suggestionsTop + i * ROW_HEIGHT;
            boolean hovered = mouseX >= suggestionsX && mouseX <= suggestionsX + 160
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hovered) {
                graphics.fill(suggestionsX, rowY, suggestionsX + 160, rowY + ROW_HEIGHT, 0x40FFFFFF);
            }
            graphics.drawString(this.font, suggestions.get(i), suggestionsX + 2, rowY + 2,
                    hovered ? 0xFFFFFF00 : 0xFFC0C0C0);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < suggestions.size(); i++) {
            int rowY = suggestionsTop + i * ROW_HEIGHT;
            if (mouseX >= suggestionsX && mouseX <= suggestionsX + 160
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                speciesBox.setValue(suggestions.get(i));
                speciesText = suggestions.get(i);
                suggestions.clear();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    /** Identifies the visible state so rows only rebuild when something a button shows changes. */
    private static String signature(List<HuntView> hunts) {
        StringBuilder builder = new StringBuilder();
        for (HuntView hunt : hunts) {
            builder.append(hunt.key()).append(':').append(hunt.total())
                    .append(hunt.countEncounters() ? 'E' : 'e')
                    .append(hunt.countEggs() ? 'G' : 'g').append('|');
        }
        return builder.toString();
    }

    private void send(HuntActionPayload payload) {
        HuntKeybinds.send(payload);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
