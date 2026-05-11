package com.otectus.arsnspells.compat;

import net.neoforged.fml.ModList;

/**
 * Single source of truth for whether Iron's Spellbooks is loaded.
 * Result is cached after the first invocation to keep the check on the
 * cast hot-path cheap.
 *
 * Any common-side reference to Iron's APIs MUST be guarded by
 * {@link #isLoaded()} or run only inside a class that this guard reaches.
 */
public final class IronsCompat {
    public static final String MODID = "irons_spellbooks";

    private static volatile Boolean cached;

    private IronsCompat() {}

    public static boolean isLoaded() {
        Boolean c = cached;
        if (c != null) {
            return c;
        }
        synchronized (IronsCompat.class) {
            if (cached == null) {
                cached = ModList.get().isLoaded(MODID);
            }
            return cached;
        }
    }
}
