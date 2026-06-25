package com.thames.shinydexlink.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server hunt control, sent from keybinds and the hunt screen. The server validates
 * every action before applying it (an action for a player with no active hunt is ignored).
 *
 * <p>{@link #action} is one of the {@code ACTION_*} constants; {@link #arg} carries extra data
 * for {@link #ACTION_SET_TARGET} as {@code "species"} or {@code "species|form"} and is otherwise
 * empty.
 */
public record HuntActionPayload(String action, String arg) implements CustomPacketPayload {

    public static final String ACTION_INCREMENT = "increment";
    public static final String ACTION_DECREMENT = "decrement";
    public static final String ACTION_RESET = "reset";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_SET_TARGET = "set_target";
    public static final String ACTION_TOGGLE_EGGS = "toggle_eggs";
    public static final String ACTION_TOGGLE_ENCOUNTERS = "toggle_encounters";

    public static final Type<HuntActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("shinydex-link", "hunt_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HuntActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, HuntActionPayload::action,
            ByteBufCodecs.STRING_UTF8, HuntActionPayload::arg,
            HuntActionPayload::new
    );

    public static HuntActionPayload of(String action) {
        return new HuntActionPayload(action, "");
    }

    @Override
    public Type<HuntActionPayload> type() {
        return TYPE;
    }
}
