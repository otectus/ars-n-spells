package com.otectus.arsnspells.compat;

import net.neoforged.fml.ModList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generalised, cached mod-presence probe — the multi-mod successor to the
 * original {@code IronsCompat.isLoaded()} double-checked cache. Mod membership
 * never changes at runtime, so each id is resolved once via {@link ModList} and
 * memoised, keeping per-cast hot-path gates cheap.
 *
 * <p>This is the lightweight compatibility foundation for 2.5.0: a shared
 * presence cache plus {@link CompatIds} constants. The fuller
 * {@code ICompatModule}/supplier framework from the compatibility plan is
 * intentionally deferred until there are enough integrations to justify it.
 *
 * @since 2.5.0
 */
public final class ModPresence {
    private static final Map<String, Boolean> CACHE = new ConcurrentHashMap<>();

    private ModPresence() {}

    /** {@code true} if {@code modid} is loaded. Cached after the first probe. */
    public static boolean isLoaded(String modid) {
        Boolean cached = CACHE.get(modid);
        if (cached != null) {
            return cached;
        }
        boolean loaded = ModList.get().isLoaded(modid);
        CACHE.put(modid, loaded);
        return loaded;
    }
}
