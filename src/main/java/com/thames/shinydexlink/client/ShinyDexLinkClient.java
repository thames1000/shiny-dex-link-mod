package com.thames.shinydexlink.client;

import com.thames.shinydexlink.net.HuntUpdatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client entrypoint: receives hunt snapshots from the server into {@link ClientHuntState} and
 * sets up the HUD overlay and keybinds. Payload codecs are registered in the common
 * initializer, so this side only registers the receiver and UI.
 */
public final class ShinyDexLinkClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientOverlayConfig.init(FabricLoader.getInstance().getConfigDir());

        ClientPlayNetworking.registerGlobalReceiver(HuntUpdatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientHuntState.update(payload)));

        HuntHudOverlay.register();
        HuntKeybinds.register();
    }
}
