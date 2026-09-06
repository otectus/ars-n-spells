package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.BridgeRouting;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.contract.ModeRoutingSnapshot;
import com.otectus.arsnspells.contract.ResourceAmount;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.contract.RoundingRule;
import com.otectus.arsnspells.contract.StandardQuotePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The loader's one entry point into {@link StandardQuotePolicy} (audit V05).
 *
 * <p>Closes the finding that a cast was priced twice, by two different pieces of arithmetic:
 * pre-cast validation computed {@code (float)(cost * rate)} while the charging seam computed
 * {@code (int) Math.round(cost * rate)}, and the Iron's handler had a third copy. The amount
 * checked could therefore differ from the amount charged, and in SEPARATE mode the check
 * applied a conversion the charge never did - so at a rate of 10 a spell was validated at ten
 * times the price it was actually billed. Check and charge now build one {@link CostRules}
 * snapshot per attempt, feed it to one policy, and read the same quote.
 *
 * <h2>Which mode name prices a leg</h2>
 *
 * <p>{@link #pricingModeName} is the only interesting decision here, and it exists to keep
 * {@link com.otectus.arsnspells.contract.ConversionKind#FLAT_LEGACY} priced exactly as this
 * repository priced it:
 *
 * <ul>
 *   <li><b>A flat rate applies only when the leg actually crosses systems.</b> The policy's
 *       unified branch multiplies by {@code flatRateFrom(origin)} unconditionally, which is the
 *       transcribed legacy arithmetic for a leg whose origin pool is <em>not</em> the
 *       authoritative one. When the origin pool already is the authoritative one there is
 *       nothing to convert, and this repository never converted there: an Ars cross-cast in
 *       ARS_PRIMARY was billed {@code base * multiplier} flat. Such a leg is therefore priced
 *       under the {@code disabled} name, which is the policy's "multiplier only, one leg, no
 *       rate" branch.</li>
 *   <li><b>The dual-cost split is a cross-cast rule.</b> {@link ModeRoutingSnapshot} routes a
 *       native spend in SEPARATE to {@link com.otectus.arsnspells.contract.BillingRoute
 *       #nativeOwned}: each system pays for its own spells out of its own pool. Only a
 *       cross-cast is split across both, so a {@link CarrierPolicy#NATIVE_ONLY} leg is never
 *       priced under the {@code separate} name.</li>
 * </ul>
 *
 * <p>Legs come back as exact doubles. The narrowing this mod's seams have always done -
 * {@code float} for a bridge spend, {@code Math.round} for an Ars cost-event field - is applied
 * at the boundary by {@link #legAsFloat} and {@link #legAsInt}, so observable costs do not
 * shift under the change to double-precision pricing.
 */
public final class AnsQuotes {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnsQuotes.class);

    /**
     * The dual-cost shares are normalized once, inside {@link CostRules}. Warn once when that
     * changed the effective price, rather than repricing silently on every cast.
     */
    private static final AtomicBoolean NORMALIZATION_WARNED = new AtomicBoolean(false);

    private AnsQuotes() {}

    // ------------------------------------------------------------------
    //  Rules
    // ------------------------------------------------------------------

    /**
     * One config snapshot for one cast attempt.
     *
     * <p>Build this once at the top of an attempt and carry it. Four handlers each reading
     * {@code AnsConfig.X.get()} at a different instant during one cast is what let an
     * {@code /ans} command landing mid-cast validate against one rate and charge another.
     */
    public static CostRules rules() {
        return rules(null);
    }

    /**
     * One config snapshot for one cast attempt by {@code player}.
     *
     * <p>The Iron's-to-Ars direction is <b>pool-aware</b> (ANS-HIGH-012): the raw config value is
     * a flat scalar, which at default pool sizes (Ars max ~100, Iron's max ~1000) makes a 50-mana
     * Iron's spell cost 50 Ars mana - half the Ars pool against a twentieth of Iron's. Scaling by
     * {@code arsMax / ironsMax} restores cost proportionality, mirroring
     * {@link com.otectus.arsnspells.bridge.ManaRegenBridge#convertIronsToArs}.
     *
     * <p>It is folded into the snapshot rather than applied at one seam, which is precisely the
     * V05 drift: the Iron's cross-cast handler charged at the pool-aware rate while
     * {@link CastingAuthority#effectiveIronsCost} validated at the raw one, so check and charge
     * disagreed by the ratio of the two pools. One rate, taken once, is now used by both.
     */
    public static CostRules rules(net.minecraft.world.entity.player.Player player) {
        double arsToIrons = 1.0d;
        double ironsToArs = 1.0d;
        double arsShare = CostRules.DEFAULT_SHARE;
        double ironsShare = CostRules.DEFAULT_SHARE;
        double multiplier = 1.0d;
        try {
            arsToIrons = AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get();
            ironsToArs = AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get();
            arsShare = AnsConfig.DUAL_COST_ARS_PERCENTAGE.get();
            ironsShare = AnsConfig.DUAL_COST_ISS_PERCENTAGE.get();
            multiplier = Math.max(0.0d, AnsConfig.CROSS_CAST_COST_MULTIPLIER.get());
        } catch (Throwable configNotLoaded) {
            // Same discipline as AnsConfig.flag: a SERVER config that has not loaded is reachable
            // in normal play (world transitions, the main menu), and throwing from a pricing read
            // is a crash where the shipped defaults are a correct answer.
            arsToIrons = 1.0d;
            ironsToArs = 1.0d;
            arsShare = CostRules.DEFAULT_SHARE;
            ironsShare = CostRules.DEFAULT_SHARE;
            multiplier = 1.0d;
        }

        CostRules rules = CostRules.of(
            BridgeManager.getCurrentMode().getConfigName(),
            arsToIrons,
            poolProportional(ironsToArs, player),
            arsShare,
            ironsShare,
            AnsConfig.getConversionKind(),
            multiplier,
            RoundingRule.HALF_UP,
            BridgeManager.routingGeneration());

        if (rules.sharesNormalized() && NORMALIZATION_WARNED.compareAndSet(false, true)) {
            LOGGER.warn("Ars 'n' Spells: dual_cost_ars_percentage ({}) and dual_cost_iss_percentage "
                    + "({}) do not sum to 1.0. They have been normalized to {} / {} so a cross-cast "
                    + "costs the spell's price split across both pools. Set them to sum to 1.0 to "
                    + "silence this.",
                arsShare, ironsShare, rules.arsShare(), rules.ironsShare());
        }
        return rules;
    }

    /**
     * {@code base} scaled by the ratio of the two pools' maxima, or unchanged when there is no
     * player to measure. {@link CostRules} rejects a non-positive rate, so a degenerate pool
     * size falls back to the raw value rather than throwing on the cast path.
     */
    private static double poolProportional(double base,
                                           net.minecraft.world.entity.player.Player player) {
        if (player == null) {
            return base;
        }
        try {
            double arsMax = com.otectus.arsnspells.util.ManaUtil.getNativeArsMaxMana(player);
            if (arsMax <= 0.0d) {
                arsMax = AnsConfig.DEFAULT_MAX_MANA.get();
            }
            double ironsMax =
                com.otectus.arsnspells.bridge.ManaRegenBridge.getCurrentIronsMaxMana(player);
            if (ironsMax <= 0.0d || arsMax <= 0.0d) {
                return base;
            }
            double scaled = base * (arsMax / ironsMax);
            return scaled > 0.0d && !Double.isInfinite(scaled) ? scaled : base;
        } catch (Throwable notAvailable) {
            return base;
        }
    }

    // ------------------------------------------------------------------
    //  Quoting
    // ------------------------------------------------------------------

    /**
     * Price one leg of one cast.
     *
     * @param baseCost the base price as the caster's own system quoted it; negatives are floored
     *                 at zero, since a negative cost credits the pool instead of draining it
     * @param origin   the unit {@code baseCost} is denominated in
     * @param carrier  the semantics of the item the cast came from; {@link
     *                 CarrierPolicy#NATIVE_ONLY} means no cross-cast multiplier applies
     * @param rules    the snapshot this attempt is priced under
     */
    public static CostQuote quote(int baseCost, ResourceUnit origin, CarrierPolicy carrier,
                                  CostRules rules) {
        return quote((double) Math.max(0, baseCost), origin, carrier, rules);
    }

    /** As {@link #quote(int, ResourceUnit, CarrierPolicy, CostRules)}, for a fractional base. */
    public static CostQuote quote(double baseCost, ResourceUnit origin, CarrierPolicy carrier,
                                  CostRules rules) {
        ResourceAmount amount = new ResourceAmount(origin, Math.max(0.0d, baseCost));
        return StandardQuotePolicy.INSTANCE.quote(amount, priced(rules, origin, carrier), carrier);
    }

    /**
     * {@code rules} restated under the mode name that prices a leg of this origin and carrier.
     * See the class javadoc: this is what keeps FLAT_LEGACY at today's numbers.
     */
    static CostRules priced(CostRules rules, ResourceUnit origin, CarrierPolicy carrier) {
        String mode = pricingModeName(rules, origin, carrier);
        if (mode.equals(rules.modeName())) {
            return rules;
        }
        return CostRules.of(mode, rules.arsToIronsRate(), rules.ironsToArsRate(),
            rules.arsShare(), rules.ironsShare(), rules.conversionKind(),
            rules.crossCastMultiplier(), rules.rounding(), rules.generation());
    }

    /** The mode name {@link StandardQuotePolicy} should price this leg under. */
    static String pricingModeName(CostRules rules, ResourceUnit origin, CarrierPolicy carrier) {
        if (rules.isUnificationDisabled()) {
            return ModeRoutingSnapshot.DISABLED;
        }
        if (rules.isDualCostMode()) {
            // A cross-cast is split across both pools; a native cast pays its own pool alone,
            // and its own pool needs no conversion, so it prices as an unconverted single leg.
            return carrier == CarrierPolicy.NATIVE_ONLY
                ? ModeRoutingSnapshot.DISABLED
                : ModeRoutingSnapshot.SEPARATE;
        }
        // A flat rate converts into the authoritative pool. A leg already denominated in that
        // pool's unit is not converted - and never was.
        return authoritativeUnit() == origin ? ModeRoutingSnapshot.DISABLED : rules.modeName();
    }

    /** The pool the live routing snapshot treats as authoritative. */
    private static ResourceUnit authoritativeUnit() {
        BridgeRouting routing = BridgeManager.routing();
        return routing != null ? routing.snapshot().authoritativeUnit() : ResourceUnit.ARS_MANA;
    }

    // ------------------------------------------------------------------
    //  Loader boundary narrowing
    // ------------------------------------------------------------------

    /**
     * The {@code unit} total of {@code quote} as the {@code float} a bridge spend takes.
     *
     * <p>The contract computes in {@code double} and deliberately does not round a converted
     * leg, because the loaders never did; narrowing belongs at the seam that always narrowed.
     */
    public static float legAsFloat(CostQuote quote, ResourceUnit unit) {
        return (float) quote.total(unit);
    }

    /**
     * The {@code unit} total of {@code quote} as the {@code int} an Ars or Iron's cost event
     * field takes, rounded by the cast's one declared {@link RoundingRule}.
     */
    public static int legAsInt(CostQuote quote, ResourceUnit unit, CostRules rules) {
        long rounded = rules.rounding().apply(quote.total(unit));
        if (rounded <= 0L) {
            return 0;
        }
        return rounded > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rounded;
    }
}
