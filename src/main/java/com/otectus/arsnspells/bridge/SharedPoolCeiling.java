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
        if (Double.isNaN(arsMax) || arsMax <= 0.0) {
            return 0.0;
        }
        double own = Double.isNaN(ironsOwnMax) ? 0.0 : ironsOwnMax;
        return Math.max(0.0, arsMax - own);
    }

    /**
     * The ceiling that results from applying {@link #modifierAmount} — i.e. what
     * {@code player.getAttributeValue(max_mana)} reports afterwards, and by construction
     * {@code max(ironsOwnMax, arsMax)}.
     */
    public static double resultingCeiling(double ironsOwnMax, double arsMax) {
        return ironsOwnMax + modifierAmount(ironsOwnMax, arsMax);
    }

    /**
     * True when writing to a pool of {@code pool} under {@code ceiling} would lose mana to
     * Iron's down-clamp rather than merely spending it.
     */
    public static boolean wouldDestroyMana(double pool, double ceiling) {
        return pool > ceiling;
    }
}
