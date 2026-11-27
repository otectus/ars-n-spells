package com.otectus.arsnspells.compat.irons_spells;

import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.config.ServerConfigs;

/**
 * Iron's-isolated read of Iron's native regeneration schedule (audit ANS-011). Only reached
 * through {@code ManaRegenBridge} after an {@code IronsCompat.isLoaded()} check.
 *
 * <p>Verified against the pinned Iron's 1.20.1-3.15.0, 1.20.1-3.16.3 and 1.21.1-3.16.3 jars:
 * {@code MagicManager.regenPlayerMana} adds {@code MAX_MANA * MANA_REGEN * 0.01 * mana_regen_multiplier}
 * per call, and {@code MagicManager.tick} calls it when {@code server.getTickCount() % MANA_REGEN_TICKS == 0}
 * with {@code MANA_REGEN_TICKS = 10}.
 */
public final class IronsRegenSettings {
    private IronsRegenSettings() {}

    /** Server ticks between Iron's regeneration calls. */
    public static int regenTicks() {
        return MagicManager.MANA_REGEN_TICKS;
    }

    /** Iron's own {@code manaRegenMultiplier} server setting, 1.0 when it cannot be read. */
    public static double multiplier() {
        try {
            Double value = ServerConfigs.MANA_REGEN_MULTIPLIER.get();
            return value == null || !Double.isFinite(value) ? 1.0 : value;
        } catch (IllegalStateException notLoaded) {
            return 1.0;
        }
    }
}
