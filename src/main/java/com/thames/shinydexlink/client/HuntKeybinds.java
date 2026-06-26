package com.thames.shinydexlink.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.thames.shinydexlink.net.HuntActionPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Client keybinds for the hunt counter. Both are purely client-side: open the hunt screen (where
 * each hunt has its own counter buttons) and hide/show the overlay. Manual +/- live per-hunt in the
 * screen now that several hunts can run at once. Default bindings can be rebound in Options -
 * Controls.
 */
public final class HuntKeybinds {
    private static final String CATEGORY = "key.categories.shinydex-link";

    private static KeyMapping openScreen;
    private static KeyMapping toggleOverlay;

    private HuntKeybinds() {
    }

    public static void register() {
        openScreen = register("key.shinydex-link.open_screen", GLFW.GLFW_KEY_H);
        toggleOverlay = register("key.shinydex-link.toggle_overlay", InputConstants.UNKNOWN.getValue());

        ClientTickEvents.END_CLIENT_TICK.register(HuntKeybinds::onClientTick);
    }

    private static KeyMapping register(String translationKey, int keyCode) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyMapping(translationKey, InputConstants.Type.KEYSYM, keyCode, CATEGORY));
    }

    private static void onClientTick(Minecraft client) {
        while (toggleOverlay.consumeClick()) {
            ClientHuntState.toggleOverlay();
        }
        while (openScreen.consumeClick()) {
            client.setScreen(new HuntScreen());
        }
    }

    static void send(HuntActionPayload payload) {
        if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(HuntActionPayload.TYPE)) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);
        }
    }
}
