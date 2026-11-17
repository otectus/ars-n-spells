package com.otectus.arsnspells.glyph;

import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import net.minecraft.server.level.ServerPlayer;

/**
 * Which resource pays an imported glyph's executions (3.4.1 review PAYMENT-01).
 *
 * <p>Decided once per occurrence at admission, by the same rules native Iron's casting applies to
 * that caster, so admission, an instant execution, a charged release and a channel pulse all agree on
 * the resource, and none of them demands a resource the final route does not spend:
 *
 * <ul>
 *   <li>{@link #AURA_NATIVE}: the Virtue Ring's aura path is active. Covenant of the Seven owns that
 *       debit at Iron's pre-cast event, for native casts and imported executions alike (both post
 *       it), and ANS takes nothing, exactly as it leaves a native cast exempt;</li>
 *   <li>{@link #LP}: the Cursed Ring's LP path is active. ANS owns the leg, as it does for a native
 *       cast: the first execution's LP is reserved at admission at the native LP formula, settled at
 *       the effect boundary against the cast event's final price, and a charged child hands it back
 *       before Iron's own pipeline prices the release in LP again;</li>
 *   <li>{@link #MANA}: everything else, routed by the session's mana mode.</li>
 * </ul>
 * The route is fixed for the session: a ring taken off mid-flight never moves accepted work into
 * another pool. A creative or recast execution is free whatever the route.
 */
public enum GlyphRoute {
    MANA,
    LP,
    AURA_NATIVE;

    /** The route native casting would use for {@code player} now. */
    public static GlyphRoute decide(ServerPlayer player) {
        try {
            if (SanctifiedLegacyCompat.isVirtueAuraCostPathActive(player)) return AURA_NATIVE;
            if (SanctifiedLegacyCompat.isCursedRingCostPathActive(player)) return LP;
        } catch (RuntimeException | LinkageError unavailable) {
            // An unreadable ring state is no ring: the mana route, as native casting falls back.
        }
        return MANA;
    }
}
