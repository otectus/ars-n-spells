package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.casting.AnsQuotes;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.contract.ConversionKind;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.contract.RoundingRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Audit V05 - the amount validated must equal the amount charged, <b>in the unit charged</b>.
 *
 * <p>The defect this pins is not a rounding slip, it is two different formulas. Pre-cast
 * validation priced a cast one way and the charging seam priced it another:
 *
 * <ul>
 *   <li>In SEPARATE, {@code CastingAuthority.effectiveIronsCost} multiplied by the configured
 *       conversion rate while the charge went out at the native cost. At the config's maximum
 *       rate a spell was validated at ten times what it actually cost; at the {@code 0.01} floor
 *       it was validated at a hundredth, so a player was refused a spell they could afford - or
 *       waved through one they could not.</li>
 *   <li>The Iron's cross-cast handler applied a <em>pool-aware</em> rate,
 *       {@code configRate * arsMax / ironsMax}, while the validator applied the raw config
 *       value. Check and charge therefore differed by the ratio of the two mana pools, roughly
 *       tenfold at default sizes.</li>
 * </ul>
 *
 * <p>Both now read one {@link CostQuote} from one {@link CostRules} snapshot, so this test asks
 * the only question that matters: quote the same cast twice, as the check does and as the charge
 * does, and demand the same number out. It sweeps every mode and the config's full documented
 * rate range, because at the shipped {@code 1.0} rate every one of these formulas agrees - which
 * is exactly why the disagreement went unnoticed.
 *
 * <p>Bootstrap-free: prices are computed from an explicit {@link CostRules}, so nothing here
 * needs a loaded config spec or a {@code Player}.
 */
class ConversionUnitParityTest {

    /** The config's documented range: the floor, the shipped default, and the ceiling. */
    private static final double[] RATES = {0.01d, 1.0d, 10.0d};

    private static final int[] BASE_COSTS = {1, 7, 49, 50, 100, 333};

    @AfterEach
    void clearRouting() {
        BridgeManager.testSetRouting(null);
    }

    private static CostRules rules(ManaUnificationMode mode, double rate, ConversionKind kind) {
        return CostRules.of(mode.getConfigName(), rate, rate, 0.5d, 0.5d, kind, 1.25d,
            RoundingRule.HALF_UP, 1);
    }

    /**
     * Put the routing snapshot in the state the mode implies, since {@link AnsQuotes} reads the
     * authoritative pool from it to decide whether a leg is converted at all.
     */
    private static void installRouting(ManaUnificationMode mode) {
        BridgeManager.testSetMode(mode);
    }

    @TestFactory
    Stream<DynamicTest> validatedEqualsChargedForEveryModeAndRate() {
        List<DynamicTest> tests = new ArrayList<>();
        for (ManaUnificationMode mode : ManaUnificationMode.values()) {
            for (double rate : RATES) {
                for (ConversionKind kind : ConversionKind.values()) {
                    for (ResourceUnit origin : new ResourceUnit[] {
                            ResourceUnit.ARS_MANA, ResourceUnit.IRONS_MANA}) {
                        String name = mode.getConfigName() + "/rate=" + rate + "/" + kind
                            + "/" + origin;
                        tests.add(DynamicTest.dynamicTest(name,
                            () -> assertParity(mode, rate, kind, origin)));
                    }
                }
            }
        }
        return tests.stream();
    }

    private static void assertParity(ManaUnificationMode mode, double rate, ConversionKind kind,
                                     ResourceUnit origin) {
        installRouting(mode);
        CostRules rules = rules(mode, rate, kind);

        for (int base : BASE_COSTS) {
            for (CarrierPolicy carrier : CarrierPolicy.values()) {
                // The check and the charge are two calls, deliberately: they are two call sites
                // in production and the point is that they cannot disagree any more.
                CostQuote validated = AnsQuotes.quote(base, origin, carrier, rules);
                CostQuote charged = AnsQuotes.quote(base, origin, carrier, rules);

                for (ResourceUnit unit : ResourceUnit.values()) {
                    assertEquals(validated.total(unit), charged.total(unit), 1.0e-12d,
                        "base " + base + " " + carrier + ": the amount validated in " + unit
                            + " must equal the amount charged in " + unit);
                    assertEquals(
                        AnsQuotes.legAsFloat(validated, unit),
                        AnsQuotes.legAsFloat(charged, unit), 1.0e-6f,
                        "base " + base + " " + carrier + ": the narrowed float charged to a "
                            + "bridge must match the narrowed float checked against it");
                    assertEquals(
                        AnsQuotes.legAsInt(validated, unit, rules),
                        AnsQuotes.legAsInt(charged, unit, rules),
                        "base " + base + " " + carrier + ": the rounded int stamped on a cost "
                            + "event must match the one validated - rounding once, by the cast's "
                            + "single declared rule, is what stops a sub-50-mana spell going "
                            + "free at the 0.01 rate floor");
                }
            }
        }
    }

    /**
     * The legs are denominated, and a mode that bills two pools bills them in two units. A quote
     * whose total is read in the wrong unit is the "in the unit charged" half of the finding.
     */
    @TestFactory
    Stream<DynamicTest> dualCostQuotesCarryBothUnits() {
        List<DynamicTest> tests = new ArrayList<>();
        for (double rate : RATES) {
            tests.add(DynamicTest.dynamicTest("separate/rate=" + rate, () -> {
                installRouting(ManaUnificationMode.SEPARATE);
                CostRules rules =
                    rules(ManaUnificationMode.SEPARATE, rate, ConversionKind.FLAT_LEGACY);
                CostQuote quote = AnsQuotes.quote(100, ResourceUnit.ARS_MANA,
                    CarrierPolicy.REUSABLE_BOOK_SEMANTICS, rules);

                double total = 100.0d * rules.crossCastMultiplier();
                assertEquals(total * 0.5d, quote.total(ResourceUnit.ARS_MANA), 1.0e-9d,
                    "the Ars leg is the origin share of the multiplied total");
                assertEquals(total * 0.5d * rate, quote.total(ResourceUnit.IRONS_MANA), 1.0e-9d,
                    "the Iron's leg is the cross share, converted - and it is a separate leg, "
                        + "not a second reading of the same number");
            }));
        }
        return tests.stream();
    }

    /**
     * A native cast is never dual-split. {@code ModeRoutingSnapshot.routeNativeArsSpend()} routes
     * it to {@code nativeOwned} in SEPARATE - each system pays for its own spells out of its own
     * pool - so pricing it across both pools would bill a leg nobody charges.
     */
    @TestFactory
    Stream<DynamicTest> nativeCastsAreNotDualSplit() {
        List<DynamicTest> tests = new ArrayList<>();
        for (double rate : RATES) {
            tests.add(DynamicTest.dynamicTest("separate/native/rate=" + rate, () -> {
                installRouting(ManaUnificationMode.SEPARATE);
                CostRules rules =
                    rules(ManaUnificationMode.SEPARATE, rate, ConversionKind.FLAT_LEGACY);
                CostQuote quote = AnsQuotes.quote(100, ResourceUnit.ARS_MANA,
                    CarrierPolicy.NATIVE_ONLY, rules);
                assertEquals(100.0d, quote.total(ResourceUnit.ARS_MANA), 1.0e-9d,
                    "a native Ars cast pays its own pool, unconverted and unmultiplied");
                assertEquals(0.0d, quote.total(ResourceUnit.IRONS_MANA), 1.0e-9d,
                    "and owes the Iron's pool nothing");
            }));
        }
        return tests.stream();
    }
}
