package com.thames.shinydexlink.cobblemon;

import net.minecraft.server.level.ServerPlayer;

public final class CobblemonPokedexReader {
    public PokedexSnapshot readSnapshot(ServerPlayer player) {
        throw new UnsupportedOperationException("Full Cobblemon Pokédex snapshot sync is planned for a future ShinyDex Link version.");
    }

    public record PokedexSnapshot(String minecraftUuid) {
    }
}
