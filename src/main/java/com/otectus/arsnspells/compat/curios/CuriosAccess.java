package com.otectus.arsnspells.compat.curios;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

/**
 * Reflection-based Curios slot scan shared by the Ars-side
 * ({@link com.otectus.arsnspells.events.CurioDiscountHandler}) and Iron's-side
 * ({@link IronsCurioDiscountHandler}) spell-discount handlers.
 *
 * <p>Curios is not pinned on the compile classpath (the target instance ships
 * the runtime jar, but {@code gradle.properties} declares no CurseMaven file id
 * for it), so we reach its API reflectively. Method handles are cached after the
 * first lookup to keep the cast hot path cheap. Every access returns 0 / fails
 * soft when Curios is absent or its API shifts, so callers never need a
 * try/catch.
 *
 * @since 2.5.0 (extracted from CurioDiscountHandler so both cast paths share it)
 */
public final class CuriosAccess {
    private static volatile boolean initAttempted = false;
    private static volatile Class<?> curiosApi;
    private static volatile Method mGetInventory;
    private static volatile Method mGetCurios;
    private static volatile Method mGetStacks;
    private static volatile Method mGetSlots;
    private static volatile Method mGetStackInSlot;

    private CuriosAccess() {}

    /**
     * Count the player's worn curios whose ItemStack carries {@code tag}.
     * Returns 0 if Curios is absent or any inventory access throws.
     */
    public static int countTagged(Player player, TagKey<Item> tag) {
        if (!resolve()) {
            return 0;
        }
        try {
            Object optional = mGetInventory.invoke(null, player);
            if (!(optional instanceof Optional<?> opt) || opt.isEmpty()) {
                return 0;
            }
            Object handler = opt.get();
            if (mGetCurios == null) mGetCurios = handler.getClass().getMethod("getCurios");
            Object slots = mGetCurios.invoke(handler);
            if (!(slots instanceof Map<?, ?> slotMap)) {
                return 0;
            }
            int count = 0;
            for (Object entry : slotMap.values()) {
                if (mGetStacks == null) mGetStacks = entry.getClass().getMethod("getStacks");
                Object inv = mGetStacks.invoke(entry);
                if (mGetSlots == null) mGetSlots = inv.getClass().getMethod("getSlots");
                int slotCount = (int) mGetSlots.invoke(inv);
                if (mGetStackInSlot == null) mGetStackInSlot =
                    inv.getClass().getMethod("getStackInSlot", int.class);
                for (int i = 0; i < slotCount; i++) {
                    Object obj = mGetStackInSlot.invoke(inv, i);
                    if (obj instanceof ItemStack stack && !stack.isEmpty() && stack.is(tag)) {
                        count++;
                    }
                }
            }
            return count;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static boolean resolve() {
        if (curiosApi != null) {
            return true;
        }
        if (initAttempted) {
            return false;
        }
        initAttempted = true;
        try {
            curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi", false,
                CuriosAccess.class.getClassLoader());
            mGetInventory = curiosApi.getMethod("getCuriosInventory", LivingEntity.class);
            return true;
        } catch (Throwable t) {
            curiosApi = null;
            return false;
        }
    }
}
