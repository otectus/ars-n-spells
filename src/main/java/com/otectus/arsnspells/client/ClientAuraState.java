package com.otectus.arsnspells.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-side mirror of the local player's aura value, updated by
 * {@link com.otectus.arsnspells.network.AuraSyncPacket}.
 *
 * <p>Stored as plain ints — the HUD only ever renders the local player, so we don't
 * need a per-UUID map. State is reset on world unload via {@link #reset()}.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientAuraState {
    private static volatile int aura = 0;
    private static volatile int maxAura = 1000;
    private static volatile boolean initialized = false;

    private ClientAuraState() {}

    public static void update(int newAura, int newMaxAura) {
        // ANS-HIGH-005 (defense-in-depth): bound the upper end so a hostile or
        // garbled payload cannot push the HUD into a "2147483647 / 1" render.
        // Cap matches AnsConfig.AURA_MAX_DEFAULT ceiling; aura is clamped to
        // [0, maxAura] so the render fraction is always in [0, 1].
        maxAura = Math.max(1, Math.min(100_000, newMaxAura));
        aura = Math.max(0, Math.min(maxAura, newAura));
        initialized = true;
    }

    public static int getAura() {
        return aura;
    }

    public static int getMaxAura() {
        return maxAura;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void reset() {
        aura = 0;
        maxAura = 1000;
        initialized = false;
    }
}
