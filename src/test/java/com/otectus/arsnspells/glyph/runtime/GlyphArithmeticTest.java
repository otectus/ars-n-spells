package com.otectus.arsnspells.glyph.runtime;

import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.contract.ConversionKind;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.contract.ResourceAmount;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.contract.RoundingRule;
import com.otectus.arsnspells.contract.StandardQuotePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Imported-glyph arithmetic: levels, cast time, speed, pulses, and the imported leg's quote in every mode. */
class GlyphArithmeticTest {

    @Test
    void amplifyAndDampenStayWithinTheCataloguedLevels() {
        assertEquals(3, GlyphArithmetic.effectiveLevel(1, 1, 10, 2, 0));
        assertEquals(3, GlyphArithmetic.effectiveLevel(1, 1, 10, 5, 0), "at most two levels of Amplify");
        assertEquals(4, GlyphArithmetic.effectiveLevel(3, 1, 4, 2, 0), "never above the spell's maximum");
        assertEquals(1, GlyphArithmetic.effectiveLevel(2, 1, 10, 0, 3), "never below the minimum");
        assertEquals(2, GlyphArithmetic.effectiveLevel(2, 1, 10, 1, 1));
    }

    @Test
    void chargeTimeScalesAndNeverReachesZero() {
        assertEquals(30, GlyphArithmetic.chargeTicks(30, 0));
        assertEquals(23, GlyphArithmetic.chargeTicks(30, 1));
        assertEquals(17, GlyphArithmetic.chargeTicks(30, 2));
        assertEquals(38, GlyphArithmetic.chargeTicks(30, -1));
        assertEquals(1, GlyphArithmetic.chargeTicks(1, 6));
        assertEquals(1, GlyphArithmetic.chargeTicks(0, 0));
        assertEquals(1.25, GlyphArithmetic.launchSpeedFactor(1), 1e-9);
        assertEquals(0.8, GlyphArithmetic.launchSpeedFactor(-1), 1e-9);
        assertEquals(4, GlyphArithmetic.extraPulses(2, 0));
        assertEquals(-2, GlyphArithmetic.extraPulses(0, 1));
    }

    private static CostRules rules(String mode, double rate, ConversionKind kind) {
        return CostRules.of(mode, rate, rate, 0.5, 0.5, kind, 1.5, RoundingRule.HALF_UP, 1);
    }

    private static CostQuote glyphLeg(String mode, double rate, ConversionKind kind, double arsMax, double ironsMax) {
        return StandardQuotePolicy.INSTANCE.quote(new ResourceAmount(ResourceUnit.IRONS_MANA, 40), rules(mode, rate, kind),
            CarrierPolicy.REUSABLE_BOOK_SEMANTICS, arsMax, ironsMax);
    }

    @Test
    void theImportedLegCarriesTheCrossMultiplierOnceAndConvertsOnlyAcrossPools() {
        // Iron's cost 40, cross-cast multiplier 1.5 -> 60 in Iron's units, rate 3 across pools.
        assertEquals(60, glyphLeg("disabled", 3, ConversionKind.FLAT_LEGACY, 1, 1).total(ResourceUnit.IRONS_MANA), 1e-9);
        assertEquals(60, glyphLeg("iss_primary", 3, ConversionKind.FLAT_LEGACY, 1, 1).total(ResourceUnit.IRONS_MANA), 1e-9);
        assertEquals(180, glyphLeg("ars_primary", 3, ConversionKind.FLAT_LEGACY, 1, 1).total(ResourceUnit.ARS_MANA), 1e-9);
        CostQuote separate = glyphLeg("separate", 3, ConversionKind.FLAT_LEGACY, 1, 1);
        assertEquals(30, separate.total(ResourceUnit.IRONS_MANA), 1e-9);
        assertEquals(90, separate.total(ResourceUnit.ARS_MANA), 1e-9);
        assertEquals(60, glyphLeg("hybrid", 3, ConversionKind.FLAT_LEGACY, 1, 1).total(ResourceUnit.IRONS_MANA), 1e-9);
    }

    @Test
    void equalPercentUsesThePoolMaxima() {
        // Iron's max 4000, Ars max 10000: 60 Iron's mana is 1.5% of the Iron's pool, 150 Ars mana.
        assertEquals(150, glyphLeg("ars_primary", 3, ConversionKind.EQUAL_PERCENT, 10000, 4000).total(ResourceUnit.ARS_MANA), 1e-9);
        assertEquals(60, glyphLeg("iss_primary", 3, ConversionKind.EQUAL_PERCENT, 10000, 4000).total(ResourceUnit.IRONS_MANA), 1e-9);
    }

    @Test
    void sameLegsComparesUnitsAndAmounts() {
        CostQuote a = glyphLeg("separate", 3, ConversionKind.FLAT_LEGACY, 1, 1);
        assertTrue(GlyphArithmetic.sameLegs(a, glyphLeg("separate", 3, ConversionKind.FLAT_LEGACY, 1, 1)));
        assertFalse(GlyphArithmetic.sameLegs(a, glyphLeg("separate", 2, ConversionKind.FLAT_LEGACY, 1, 1)));
        assertFalse(GlyphArithmetic.sameLegs(a, glyphLeg("iss_primary", 3, ConversionKind.FLAT_LEGACY, 1, 1)));
    }
}
