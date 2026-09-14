package com.otectus.arsnspells.bridge;

/**
 * Arithmetic for the shared mana pool's ceiling.
 *
 * <p>Iron's Spellbooks clamps <em>every</em> write to a player's mana down to their
 * {@code max_mana} attribute — {@code MagicData.setMana} ends in
 * {@code if (mana > maxAttr) mana = maxAttr;}, and {@code addMana} is just
 * {@code setMana(mana + delta)}. A ceiling that sits below the current pool therefore does
 * not cap the pool, it deletes the difference, on the next write of any kind. When ANS runs
 * a shared pool it owns that attribute, so it has to keep it at or above whatever maximum
 * the Ars side is reporting to the player.
 *
 * <p>Kept free of any Ars or Iron's import so the rule can be unit-tested without a
 * Minecraft bootstrap; {@code EquipmentIntegration} supplies the live attribute values.
 */
public final class SharedPoolCeiling {

    private SharedPoolCeiling() {}

    /**
     * How much the ANS ceiling modifier must add for the shared pool to hold {@code arsMax}.
     *
     * <p>{@code ironsOwnMax} is Iron's max mana <em>with the ANS modifier taken out</em> —
     * its base plus its own gear, upgrade orbs and third-party affixes. Measuring against
     * that rather than against the bare base is what makes the result exactly
     * {@code max(arsMax, Iron's own max)}: measuring against the base would re-add the
     * shortfall on top of Iron's gear and quietly inflate the pool past what either system
     * intends.
     *
     * @param ironsOwnMax Iron's max mana excluding the ANS ceiling modifier
     * @param arsMax      Ars's real max (base + glyph bonus + book tier + perks, less reserve)
     * @return the additive modifier amount; never negative, and 0 when Iron's already fits Ars
     */
    public static double modifierAmount(double ironsOwnMax, double arsMax) {
        return modifierAmount(ironsOwnMax, arsMax, 1.0);
    }

    /**
     * The same amount, for an attribute whose additive modifiers are amplified downstream
     * (audit V13).
     *
     * <p>An {@code ADDITION} modifier is applied before any {@code MULTIPLY_BASE} or
     * {@code MULTIPLY_TOTAL} on the same attribute, so on an attribute a third-party mod has
     * doubled, one additive point is worth two points of final value. Subtracting the raw
     * shortfall from the already-multiplied total - which is what this method used to do -
     * therefore overshot by exactly that factor and never converged: the recompute measured
     * the native total again, asked for the same shortfall again, and the ceiling stayed a
     * third above where either system wanted it.
     *
     * <p>{@code ironsOwnMax} must be the <em>isolated native snapshot</em>: the attribute's
     * value with every ANS-owned modifier removed. {@code amplification} is what one additive
     * point is worth on that same isolated attribute, measured rather than assumed. An
     * amplification that could not be measured degrades to {@code 1.0}, which reproduces the
     * historical arithmetic - wrong, but bounded, and never a division by zero.
     *
     * <p>This is the minimal V13 fix. A full contribution ledger that separates base, gear,
     * perk and effect sources is 3.4.0 work and is deliberately not built here.
     *
     * @param ironsOwnMax   Iron's max mana with every ANS modifier removed
     * @param arsMax        Ars's real max
     * @param amplification final value gained per additive point on this attribute
     */
    public static double modifierAmount(double ironsOwnMax, double arsMax, double amplification) {
        if (Double.isNaN(arsMax) || arsMax <= 0.0) {
            return 0.0;
        }
        double own = Double.isNaN(ironsOwnMax) ? 0.0 : ironsOwnMax;
        double shortfall = Math.max(0.0, arsMax - own);
        if (shortfall == 0.0) {
            return 0.0;
        }
        double factor = (Double.isNaN(amplification) || Double.isInfinite(amplification)
            || amplification <= 0.0) ? 1.0 : amplification;
        return shortfall / factor;
    }

    /**
     * The ceiling that results from applying {@link #modifierAmount} — i.e. what
     * {@code player.getAttributeValue(max_mana)} reports afterwards, and by construction
     * {@code max(ironsOwnMax, arsMax)}.
     */
    public static double resultingCeiling(double ironsOwnMax, double arsMax) {
        return resultingCeiling(ironsOwnMax, arsMax, 1.0);
    }

    /** {@link #resultingCeiling(double, double)} for an amplified attribute (audit V13). */
    public static double resultingCeiling(double ironsOwnMax, double arsMax, double amplification) {
        double factor = (Double.isNaN(amplification) || Double.isInfinite(amplification)
            || amplification <= 0.0) ? 1.0 : amplification;
        return ironsOwnMax + factor * modifierAmount(ironsOwnMax, arsMax, amplification);
    }

    /**
     * True when writing to a pool of {@code pool} under {@code ceiling} would lose mana to
     * Iron's down-clamp rather than merely spending it.
     */
    public static boolean wouldDestroyMana(double pool, double ceiling) {
        return pool > ceiling;
    }
}
