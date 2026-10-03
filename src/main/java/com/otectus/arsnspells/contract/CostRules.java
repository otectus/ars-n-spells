package com.otectus.arsnspells.contract;

import java.util.Locale;
import java.util.Objects;

/**
 * An immutable snapshot of every config value that prices a cast.
 *
 * <p>Closes the audit finding that pricing read the live config once per seam. Four handlers
 * each called {@code AnsConfig.X.get()} at a different instant during a single cast, so an
 * {@code /ans} command or a config reload landing mid-cast could validate against one rate and
 * charge against another. A cast takes one snapshot, carries it, and stamps its
 * {@link #generation()} onto the resulting {@link CostQuote}; a quote whose generation no longer
 * matches the live snapshot is stale and must be re-quoted rather than charged.
 *
 * <p>The dual-cost shares are normalized exactly once, here. Two fallbacks are defined
 * explicitly rather than left to the arithmetic:
 * <ul>
 *   <li><b>Shares that do not sum to one</b> are divided by their sum. A pack that configured
 *       {@code 1.0 / 1.0} previously paid twice the spell's price, split across two pools,
 *       because the legacy code multiplied by each raw share independently.</li>
 *   <li><b>A zero total</b> ({@code 0.0 / 0.0}) falls back to the shipped default split,
 *       {@code 0.5 / 0.5}. It cannot normalize (there is nothing to divide by), and the two
 *       obvious alternatives are both wrong: leaving it at zero makes every dual-cost spell
 *       free, and charging {@code 1.0 / 1.0} silently doubles the price. The shipped default is
 *       the only choice that is neither an exploit nor a hidden nerf.</li>
 * </ul>
 * Either fallback sets {@link #sharesNormalized()} so a caller can warn once instead of pricing
 * differently in silence.
 *
 * @param modeName            the mana-unification mode's config name, lower-cased
 * @param arsToIronsRate      flat conversion rate applied to an Ars-origin cross leg
 * @param ironsToArsRate      flat conversion rate applied to an Iron's-origin cross leg
 * @param arsShare            normalized share of a dual cost drawn from the Ars pool
 * @param ironsShare          normalized share of a dual cost drawn from the Iron's pool
 * @param conversionKind      which conversion policy prices a cross leg
 * @param crossCastMultiplier global multiplier applied to a cross-cast's base price
 * @param rounding            the single rounding rule for this cast's unit boundaries
 * @param generation          monotonic config generation; stamped onto every quote
 * @param sharesNormalized    derived, not supplied: whether normalization or the zero-total
 *                            fallback changed the effective price. The value passed to the
 *                            canonical constructor is ignored; use {@link #of}.
 */
public record CostRules(
    String modeName,
    double arsToIronsRate,
    double ironsToArsRate,
    double arsShare,
    double ironsShare,
    ConversionKind conversionKind,
    double crossCastMultiplier,
    RoundingRule rounding,
    int generation,
    boolean sharesNormalized
) {
    /** The shipped dual-cost split, and the documented zero-total fallback. */
    public static final double DEFAULT_SHARE = 0.5d;

    /** Shares whose sum is within this of {@code 1.0} are already normalized. */
    private static final double SHARE_EPSILON = 1.0e-9d;

    public CostRules {
        Objects.requireNonNull(modeName, "modeName");
        Objects.requireNonNull(conversionKind, "conversionKind");
        Objects.requireNonNull(rounding, "rounding");
        modeName = modeName.trim().toLowerCase(Locale.ROOT);

        arsToIronsRate = requireFinitePositive(arsToIronsRate, "arsToIronsRate");
        ironsToArsRate = requireFinitePositive(ironsToArsRate, "ironsToArsRate");
        crossCastMultiplier = requireFiniteNonNegative(crossCastMultiplier, "crossCastMultiplier");
        arsShare = requireFiniteNonNegative(arsShare, "arsShare");
        ironsShare = requireFiniteNonNegative(ironsShare, "ironsShare");

        double total = arsShare + ironsShare;
        if (total <= 0.0d) {
            arsShare = DEFAULT_SHARE;
            ironsShare = DEFAULT_SHARE;
            sharesNormalized = true;
        } else if (Math.abs(total - 1.0d) > SHARE_EPSILON) {
            arsShare = arsShare / total;
            ironsShare = ironsShare / total;
            sharesNormalized = true;
        } else {
            sharesNormalized = false;
        }
    }

    /**
     * Build a snapshot from raw config values. This is the intended entry point:
     * {@code sharesNormalized} is derived, so there is nothing sensible for a caller to pass.
     */
    public static CostRules of(
        String modeName,
        double arsToIronsRate,
        double ironsToArsRate,
        double rawArsShare,
        double rawIronsShare,
        ConversionKind conversionKind,
        double crossCastMultiplier,
        RoundingRule rounding,
        int generation
    ) {
        return new CostRules(modeName, arsToIronsRate, ironsToArsRate, rawArsShare, rawIronsShare,
            conversionKind, crossCastMultiplier, rounding, generation, false);
    }

    /** A repeated synchronization may change the generation without changing the price. */
    public boolean samePricingAs(CostRules other) {
        return modeName.equals(other.modeName) && arsToIronsRate == other.arsToIronsRate
            && ironsToArsRate == other.ironsToArsRate && arsShare == other.arsShare
            && ironsShare == other.ironsShare && conversionKind == other.conversionKind
            && crossCastMultiplier == other.crossCastMultiplier && rounding == other.rounding;
    }

    /** Whether this mode splits a cross-system cast across both pools. */
    public boolean isDualCostMode() {
        return "separate".equals(modeName);
    }

    /** Whether mana unification is off, in which case no conversion applies at all. */
    public boolean isUnificationDisabled() {
        return "disabled".equals(modeName);
    }

    /** The flat rate that converts an amount denominated in {@code origin} into the other unit. */
    public double flatRateFrom(ResourceUnit origin) {
        if (origin == ResourceUnit.ARS_MANA) {
            return arsToIronsRate;
        }
        if (origin == ResourceUnit.IRONS_MANA) {
            return ironsToArsRate;
        }
        // LP and aura are not part of the Ars/Iron's exchange; they are priced natively.
        return 1.0d;
    }

    /** The dual-cost share drawn from {@code unit}'s pool. */
    public double shareFor(ResourceUnit unit) {
        if (unit == ResourceUnit.ARS_MANA) {
            return arsShare;
        }
        if (unit == ResourceUnit.IRONS_MANA) {
            return ironsShare;
        }
        return 1.0d;
    }

    private static double requireFinitePositive(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0d) {
            throw new IllegalArgumentException(name + " must be finite and positive, got " + value);
        }
        return value;
    }

    private static double requireFiniteNonNegative(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0d) {
            throw new IllegalArgumentException(name + " must be finite and non-negative, got " + value);
        }
        return value;
    }
}
