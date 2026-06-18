package com.otectus.arsnspells.compat;

/**
 * Single source of truth for whether Iron's Spellbooks is loaded.
 *
 * <p>Retained for source-compatibility with the many call sites that already use
 * it; as of 2.5.0 it delegates to {@link ModPresence} so all presence checks
 * share one cache. Any common-side reference to Iron's APIs MUST be guarded by
 * {@link #isLoaded()} or run only inside a class that this guard reaches.
 */
public final class IronsCompat {
    public static final String MODID = CompatIds.IRONS_SPELLBOOKS;

    private IronsCompat() {}

    public static boolean isLoaded() {
        return ModPresence.isLoaded(MODID);
    }
}
