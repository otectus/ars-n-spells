package com.otectus.arsnspells.util;

import com.otectus.arsnspells.bridge.ArsNativeBridge;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.IManaBridge;
import net.minecraft.world.entity.player.Player;

/**
 * Mana introspection helper. The 1.20.1 implementation reached into Ars's
 * Forge {@code IManaCap} capability via {@link Player#getCapability(...)}.
 * NeoForge 1.21.1 removed Forge capabilities and AN 5.x exposes mana via
 * its own attachment + public API surface. Route everything through
 * {@link IManaBridge} so callers stay agnostic of upstream's storage
 * shape, and lazily create a fresh {@link ArsNativeBridge} if the
 * {@link BridgeManager} hasn't been initialised yet.
 */
public final class ManaUtil {
    private ManaUtil() {}

    public static float getNativeArsMana(Player player) {
        if (player == null) return 0f;
        IManaBridge bridge = chooseArsBridge();
        return bridge != null ? bridge.getMana(player) : 0f;
    }

    public static float getNativeArsMaxMana(Player player) {
        if (player == null) return 0f;
        IManaBridge bridge = chooseArsBridge();
        return bridge != null ? bridge.getMaxMana(player) : 0f;
    }

    private static IManaBridge chooseArsBridge() {
        IManaBridge active = BridgeManager.getBridge();
        if (active instanceof ArsNativeBridge) return active;
        IManaBridge secondary = BridgeManager.getSecondaryBridge();
        if (secondary instanceof ArsNativeBridge) return secondary;
        return new ArsNativeBridge();
    }
}
