package com.otectus.arsnspells.progression;

import net.minecraft.server.level.ServerPlayer;

/**
 * Shared helper for the cross-mod progression system. Looks up the Iron's
 * Spellbooks {@code <school>_spell_power} attribute and applies a transient
 * additive modifier sized by {@code bonus}.
 *
 * Used by both Ars-side {@link com.otectus.arsnspells.events.ProgressionHandler}
 * and Iron's-side {@link com.otectus.arsnspells.events.IronsProgressionHandler}
 * so both directions share the exact same modifier id.
 *
 * <p>3.3.0: the id and the apply/remove sequence moved to {@link ProgressionModifiers}, which
 * is the single owner of the modifier's identity (audit V07). This class stays as the name the
 * Iron's-side handler calls; it adds nothing but the delegation.
 *
 * <p>Iron's-only at runtime: the attribute it looks up only exists when Iron's
 * is loaded. Callers must be gated on {@link com.otectus.arsnspells.compat.IronsCompat#isLoaded()}.
 */
public final class ProgressionAttributes {
    private ProgressionAttributes() {}

    public static void applyTransientBonus(ServerPlayer player, String school, double bonus) {
        ProgressionModifiers.apply(player, school, bonus);
    }
}
