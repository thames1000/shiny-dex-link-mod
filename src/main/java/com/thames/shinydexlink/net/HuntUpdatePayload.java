package com.thames.shinydexlink.net;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client snapshot of all of a player's hunts, used to drive the HUD overlay and hunt
 * screen. Sent whenever any hunt changes (target set, counter moved, hunt finished/stopped). An
 * empty {@link #hunts} list means the player has no active hunt and the overlay hides.
 */
public record HuntUpdatePayload(List<Entry> hunts) implements CustomPacketPayload {

    /** One hunt's renderable snapshot. {@code species}+{@code form} reconstruct the hunt key. */
    public record Entry(
            String species,
            String displayName,
            String form,
            int total,
            int encounters,
            int eggs,
            boolean countEncounters,
            boolean countEggs
    ) {
    }

    public static final Type<HuntUpdatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("shinydex-link", "hunt_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HuntUpdatePayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.hunts.size());
                for (Entry entry : payload.hunts) {
                    buf.writeUtf(entry.species() == null ? "" : entry.species());
                    buf.writeUtf(entry.displayName() == null ? "" : entry.displayName());
                    buf.writeUtf(entry.form() == null ? "" : entry.form());
                    buf.writeVarInt(entry.total());
                    buf.writeVarInt(entry.encounters());
                    buf.writeVarInt(entry.eggs());
                    buf.writeBoolean(entry.countEncounters());
                    buf.writeBoolean(entry.countEggs());
                }
            },
            buf -> {
                int count = buf.readVarInt();
                List<Entry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    entries.add(new Entry(
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readBoolean(),
                            buf.readBoolean()
                    ));
                }
                return new HuntUpdatePayload(entries);
            }
    );

    /** The "no active hunt" snapshot that tells the client to hide the overlay. */
    public static HuntUpdatePayload inactive() {
        return new HuntUpdatePayload(List.of());
    }

    @Override
    public Type<HuntUpdatePayload> type() {
        return TYPE;
    }
}
