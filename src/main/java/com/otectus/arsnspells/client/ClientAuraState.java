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
        aura = Math.max(0, newAura);
        maxAura = Math.max(1, newMaxAura);
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
