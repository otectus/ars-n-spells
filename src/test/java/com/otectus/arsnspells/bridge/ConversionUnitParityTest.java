package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.contract.ConversionKind;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.contract.ResourceAmount;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.contract.RoundingRule;
import com.otectus.arsnspells.contract.StandardQuotePolicy;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit V05 - the amount validated must equal the amount charged, in the unit charged.
 *
 * <p>The regression: validation and charging each carried their own copy of the conversion.
 * {@code CastingAuthority} multiplied by a directional rate; {@code BridgeManager} then split
 * the same figure by the dual-cost percentages and charged that. At the shipped 1:1 rate the
 * two coincided, which is why it survived; move the rate off 1.0 and a cast validated against
 * one number and took another - in SEPARATE it took a converted rate <em>and</em> a share, so
 * the price checked and the price paid were different quantities in different units.
 *
 * <p>Both paths now go through one {@link CostQuote}. This asserts the property that makes
 * that worth doing: a pool holding exactly the quoted legs is exactly emptied, and a pool one
 * unit short is refused rather than partly drained.
 */
class ConversionUnitParityTest {

    private static final double[] RATES = {0.01d, 1.0d, 10.0d};

    private static final class SpyBridge implements IManaBridge {
        private final String type;
        float pool;

        SpyBridge(String type, float pool) {
            this.type = type;
            this.pool = pool;
        }

        @Override public float getMana(Player player) { return pool; }
        @Override public void setMana(Player player, float amount) { pool = amount; }
        @Override public boolean consumeMana(Player player, float amount) {
            if (pool < amount) {
                return false;
            }
            pool -= amount;
            return true;
        }
        @Override public void addMana(Player player, float amount) { pool += amount; }
        @Override public float getMaxMana(Player player) { return Float.MAX_VALUE; }
        @Override public String getBridgeType() { return type; }
    }

    @AfterEach
    void resetRouting() {
        BridgeManager.testSetRouting(ManaUnificationMode.DISABLED, false,
            new SpyBridge("ARS_NATIVE", 0.0f), null);
    }

    private static CostRules rulesFor(ManaUnificationMode mode, double rate) {
        return CostRules.of(mode.getConfigName(), rate, rate, 0.5d, 0.5d,
            ConversionKind.FLAT_LEGACY, 1.25d, RoundingRule.HALF_UP, 1);
    }

    /** The pool that a leg denominated in {@code unit} is actually billed to. */
    private static SpyBridge poolFor(ManaUnificationMode mode, ResourceUnit unit,
                                     SpyBridge ars, SpyBridge irons) {
        boolean shared = mode == ManaUnificationMode.ISS_PRIMARY
            || mode == ManaUnificationMode.ARS_PRIMARY
            || mode == ManaUnificationMode.HYBRID;
        if (!shared) {
            return unit == ResourceUnit.IRONS_MANA ? irons : ars;
        }
        boolean ironsAuthoritative = mode == ManaUnificationMode.ISS_PRIMARY
            || mode == ManaUnificationMode.HYBRID;
        return ironsAuthoritative ? irons : ars;
    }

    @Test
    void everyModeAndRate_chargesExactlyWhatItValidated() {
        for (ManaUnificationMode mode : ManaUnificationMode.values()) {
            for (double rate : RATES) {
                for (ResourceUnit origin : new ResourceUnit[]{
                        ResourceUnit.ARS_MANA, ResourceUnit.IRONS_MANA}) {
                    String where = mode.getConfigName() + "/rate=" + rate + "/origin=" + origin;

                    CostRules rules = rulesFor(mode, rate);
                    CostQuote quote = StandardQuotePolicy.INSTANCE.quote(
                        new ResourceAmount(origin, 200.0d), rules,
                        CarrierPolicy.REUSABLE_BOOK_SEMANTICS);

                    SpyBridge ars = new SpyBridge("ARS_NATIVE", 0.0f);
                    SpyBridge irons = new SpyBridge("IRONS_SPELLS", 0.0f);
                    // Fund each pool with precisely the legs it is going to be asked for.
                    for (ResourceAmount leg : quote.legs()) {
                        poolFor(mode, leg.unit(), ars, irons).pool += (float) leg.amount();
                    }
                    BridgeManager.testSetRouting(mode, true, ars, irons);

                    assertTrue(BridgeManager.canAffordQuote(null, quote),
                        where + ": a pool holding exactly the quote must validate");
                    assertTrue(BridgeManager.consumeQuote(null, quote),
                        where + ": what validated must charge");

                    assertEquals(0.0f, ars.pool, 1.0e-3f,
                        where + ": the Ars pool must be emptied to the unit, not to a "
                            + "differently-converted figure");
                    assertEquals(0.0f, irons.pool, 1.0e-3f,
                        where + ": the Iron's pool must be emptied to the unit");
                }
            }
        }
    }

    @Test
    void everyModeAndRate_refusesRatherThanPartlyDraining() {
        for (ManaUnificationMode mode : ManaUnificationMode.values()) {
            for (double rate : RATES) {
                String where = mode.getConfigName() + "/rate=" + rate;

                CostRules rules = rulesFor(mode, rate);
                CostQuote quote = StandardQuotePolicy.INSTANCE.quote(
                    new ResourceAmount(ResourceUnit.ARS_MANA, 200.0d), rules,
                    CarrierPolicy.REUSABLE_BOOK_SEMANTICS);

                SpyBridge ars = new SpyBridge("ARS_NATIVE", 0.0f);
                SpyBridge irons = new SpyBridge("IRONS_SPELLS", 0.0f);
                for (ResourceAmount leg : quote.legs()) {
                    poolFor(mode, leg.unit(), ars, irons).pool += (float) leg.amount();
                }
                // Take one unit off whichever pool pays the origin leg.
                SpyBridge shortPool = poolFor(mode, ResourceUnit.ARS_MANA, ars, irons);
                shortPool.pool -= 1.0f;
                float arsBefore = ars.pool;
                float ironsBefore = irons.pool;

                BridgeManager.testSetRouting(mode, true, ars, irons);

                assertFalse(BridgeManager.canAffordQuote(null, quote),
                    where + ": one unit short must not validate");
                assertFalse(BridgeManager.consumeQuote(null, quote),
                    where + ": one unit short must not charge");
                assertEquals(arsBefore, ars.pool, 1.0e-4f,
                    where + ": a refused cast must leave the Ars pool untouched");
                assertEquals(ironsBefore, irons.pool, 1.0e-4f,
                    where + ": a refused cast must leave the Iron's pool untouched");
            }
        }
    }

    @Test
    void dualCostLegsAreDenominatedInDifferentUnits_notOneUnitCountedTwice() {
        // The unit half of V05: in SEPARATE the two legs are not the same quantity, and the
        // Iron's leg carries the flat rate while the Ars leg does not.
        CostRules rules = rulesFor(ManaUnificationMode.SEPARATE, 10.0d);
        CostQuote quote = StandardQuotePolicy.INSTANCE.quote(
            new ResourceAmount(ResourceUnit.ARS_MANA, 200.0d), rules,
            CarrierPolicy.REUSABLE_BOOK_SEMANTICS);

        double total = 200.0d * 1.25d;
        assertEquals(total * 0.5d, quote.total(ResourceUnit.ARS_MANA), 1.0e-6d,
            "the origin leg is the share of the total, unconverted");
        assertEquals(total * 0.5d * 10.0d, quote.total(ResourceUnit.IRONS_MANA), 1.0e-6d,
            "the cross leg is the share of the total, converted once");
    }
}
