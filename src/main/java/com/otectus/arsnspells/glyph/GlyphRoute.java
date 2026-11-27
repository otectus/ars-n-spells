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
 *
 * <p><b>Execution-time authorization (audit ANS-001).</b> Fixing the route is not enough when the
 * provider re-checks equipment: Covenant's Virtue handler takes aura only while the caster wears
 * Covenant's own Virtue Ring without Enigmatic's Cursed Ring, and only with ANS's aura integration on.
 * A delayed, projectile, repeated, charged or channelled execution therefore asks
 * {@link #refusal} before it posts any Iron's event: the route native casting would use now must
 * still be the route admitted, and an aura route must still be one Covenant will actually charge.
 * Otherwise the execution is refused before its effect and gives back only what it still holds; it
 * is never marked paid and never repriced into mana or LP.
 */
public enum GlyphRoute {
    MANA,
    LP,
    AURA_NATIVE;

    /** The route native casting would use for {@code player} now, from freshly read equipment. */
    public static GlyphRoute decide(ServerPlayer player) {
        try {
            // ANS-003: a payment decision reads the worn rings now, never a TTL-cached scan.
            SanctifiedLegacyCompat.freshState(player);
            if (SanctifiedLegacyCompat.isVirtueAuraCostPathActive(player)) return AURA_NATIVE;
            if (SanctifiedLegacyCompat.isCursedRingCostPathActive(player)) return LP;
        } catch (RuntimeException | LinkageError unavailable) {
            // An unreadable ring state is no ring: the mana route, as native casting falls back.
        }
        return MANA;
    }

    /**
     * Whether Covenant will service an aura route for {@code player} now: Covenant's own Virtue
     * Ring, no Cursed Ring, the aura integration on. A ring that is only in
     * {@code #ars_n_spells:virtue_rings} is not one Covenant charges for.
     */
    public static boolean auraProviderServices(ServerPlayer player) {
        try {
            return SanctifiedLegacyCompat.isCovenantNativeVirtueEligible(player);
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    /**
     * The refusal for executing an occurrence admitted on {@code admitted} for {@code player} now,
     * or null when it is still authorized. A null {@code admitted} (a free admission) is always
     * authorized: it pays nothing on any route.
     */
    public static String refusal(ServerPlayer player, GlyphRoute admitted) {
        if (admitted == null) return null;
        GlyphRoute now = decide(player);
        if (now != admitted) return "message.ars_n_spells.glyph.refused.route_changed";
        if (admitted == AURA_NATIVE && !auraProviderServices(player)) return "message.ars_n_spells.glyph.refused.aura_unsupported";
        return null;
    }
}
