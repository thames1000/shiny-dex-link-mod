package com.thames.shinydexlink.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client snapshot of a player's hunt, used to drive the HUD overlay. Sent whenever
 * the hunt changes (target set, counter moved, hunt finished/stopped). When {@link #active}
 * is false the overlay hides; the other fields are then unused.
 */
public record HuntUpdatePayload(
        boolean active,
        String species,
        String displayName,
        String form,
        int total,
        int encounters,
        int eggs,
        boolean countEncounters,
        boolean countEggs
) implements CustomPacketPayload {

    public static final Type<HuntUpdatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("shinydex-link", "hunt_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HuntUpdatePayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.active);
                buf.writeUtf(payload.species == null ? "" : payload.species);
                buf.writeUtf(payload.displayName == null ? "" : payload.displayName);
                buf.writeUtf(payload.form == null ? "" : payload.form);
                buf.writeVarInt(payload.total);
                buf.writeVarInt(payload.encounters);
                buf.writeVarInt(payload.eggs);
                buf.writeBoolean(payload.countEncounters);
                buf.writeBoolean(payload.countEggs);
            },
            buf -> new HuntUpdatePayload(
                    buf.readBoolean(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean()
            )
    );

    /** The "no active hunt" snapshot that tells the client to hide the overlay. */
    public static HuntUpdatePayload inactive() {
        return new HuntUpdatePayload(false, "", "", "", 0, 0, 0, true, true);
    }

    @Override
    public Type<HuntUpdatePayload> type() {
        return TYPE;
    }
}
