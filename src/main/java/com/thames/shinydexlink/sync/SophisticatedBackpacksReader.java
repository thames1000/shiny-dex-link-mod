package com.thames.shinydexlink.sync;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Best-effort reader for Sophisticated Backpacks contents, via reflection so the
 * mod stays buildable without a Sophisticated Backpacks / Core dependency.
 *
 * <p><b>This is intentionally defensive.</b> The reflected API path below matches
 * the common Sophisticated Backpacks shape (CapabilityBackpackWrapper →
 * inventory handler → slots). If your installed version exposes a different
 * surface, the read fails gracefully: it logs one warning and skips backpacks,
 * while the vanilla inventory / ender chest / shulker scan still works. Adjust
 * the class/method names here to match your version if needed.
 */
final class SophisticatedBackpacksReader {
    private static final String NAMESPACE = "sophisticatedbackpacks";
    private static volatile boolean warned = false;

    private SophisticatedBackpacksReader() {
    }

    static List<ItemStack> contents(ItemStack stack, Logger logger) {
        List<ItemStack> out = new ArrayList<>();
        if (stack == null || stack.isEmpty()) {
            return out;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !NAMESPACE.equals(id.getNamespace())) {
            return out;
        }
        try {
            Class<?> capability = Class.forName(
                    "net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper");
            Method getWrapper = capability.getMethod("getWrapperForStack", ItemStack.class);
            Object wrapper = getWrapper.invoke(null, stack);
            if (wrapper == null) {
                return out;
            }
            Object handler = wrapper.getClass().getMethod("getInventoryHandler").invoke(wrapper);
            if (handler == null) {
                return out;
            }
            int slots = (int) handler.getClass().getMethod("getSlots").invoke(handler);
            Method getStackInSlot = handler.getClass().getMethod("getStackInSlot", int.class);
            for (int i = 0; i < slots; i++) {
                Object slot = getStackInSlot.invoke(handler, i);
                if (slot instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                    out.add(itemStack);
                }
            }
        } catch (Throwable throwable) {
            if (!warned) {
                warned = true;
                logger.warn("ShinyDex: could not read Sophisticated Backpacks contents "
                        + "(API mismatch?); skipping backpacks. Cause: {}", throwable.toString());
            }
            out.clear();
        }
        return out;
    }
}
