package com.thames.shinydexlink.sync;

import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.slf4j.Logger;

/**
 * Scans a player's storage for Cobblemon berries and returns the canonical
 * Shiny Dex berry ids (e.g. {@code cobblemon:occa_berry} → {@code occa}).
 *
 * <p>Looks at the main inventory (incl. armor/offhand), the ender chest, and
 * nested vanilla container items (shulker boxes, etc.) via the CONTAINER data
 * component. If Sophisticated Backpacks is installed, backpack contents are read
 * best-effort through {@link SophisticatedBackpacksReader}.
 *
 * <p>No Cobblemon (or Sophisticated Backpacks) compile-time dependency: berries
 * are detected purely by registry id, so this works whether or not those mods
 * are present.
 */
public final class BerryScanner {
    private static final int MAX_DEPTH = 6;
    private static final String SB_MOD = "sophisticatedbackpacks";

    private BerryScanner() {
    }

    public static Set<String> scan(ServerPlayer player, Logger logger) {
        Set<String> found = new TreeSet<>();
        scanContainer(player.getInventory(), found, 0, logger);
        scanContainer(player.getEnderChestInventory(), found, 0, logger);
        return found;
    }

    private static void scanContainer(Container container, Set<String> found, int depth, Logger logger) {
        if (container == null || depth > MAX_DEPTH) {
            return;
        }
        int size = container.getContainerSize();
        for (int i = 0; i < size; i++) {
            scanStack(container.getItem(i), found, depth, logger);
        }
    }

    private static void scanStack(ItemStack stack, Set<String> found, int depth, Logger logger) {
        if (stack == null || stack.isEmpty() || depth > MAX_DEPTH) {
            return;
        }
        String berry = berryId(stack);
        if (berry != null) {
            found.add(berry);
        }

        // Vanilla container items (shulker boxes and anything using CONTAINER).
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            for (ItemStack inner : contents.nonEmptyItems()) {
                scanStack(inner, found, depth + 1, logger);
            }
        }

        // Sophisticated Backpacks (optional; reflection, never fatal).
        if (FabricLoader.getInstance().isModLoaded(SB_MOD)) {
            for (ItemStack inner : SophisticatedBackpacksReader.contents(stack, logger)) {
                scanStack(inner, found, depth + 1, logger);
            }
        }
    }

    /** {@code cobblemon:occa_berry} → {@code occa}; null if not a Cobblemon berry. */
    static String berryId(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !"cobblemon".equals(id.getNamespace())) {
            return null;
        }
        String path = id.getPath();
        if (!path.endsWith("_berry")) {
            return null;
        }
        return path.substring(0, path.length() - "_berry".length()).replace('_', '-');
    }
}
