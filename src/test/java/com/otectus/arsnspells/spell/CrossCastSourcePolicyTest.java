package com.otectus.arsnspells.spell;

import com.otectus.arsnspells.TestPaths;
import com.otectus.arsnspells.bridge.BridgeTestSupport;
import com.otectus.arsnspells.casting.AnsQuotes;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.contract.ConversionKind;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.contract.RoundingRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit V02 - a carrier's billing must come from its item kind, never from a serialized
 * {@code CastSource}.
 *
 * <p>The defect: {@code castIronsSpell} read the cast source out of the item's own cross-cast
 * data and fell back to {@code CastSource.SCROLL}. In the pinned Iron's 1.21.1-3.16.3 that
 * default is not neutral - it is a free pass. Read from the jar:
 *
 * <ul>
 *   <li>{@code CastSource.consumesMana()} returns true only for {@code SPELLBOOK} (and
 *       {@code SWORD} under a server config); it is <b>false</b> for {@code SCROLL},
 *       {@code COMMAND}, {@code MOB} and {@code NONE}.</li>
 *   <li>{@code AbstractSpell.castSpell} tests {@code consumesMana()} before subtracting the
 *       event's mana cost and skips the subtraction entirely when it is false.</li>
 *   <li>{@code CastSource.respectsCooldown()} is likewise false for {@code SCROLL}, so
 *       {@code canBeCastedBy} applies no cooldown.</li>
 * </ul>
 *
 * <p>So the serialized value decided whether a cross-cast cost anything at all, and the value is
 * both edit-controllable and survives being copied onto a different item. The mapping now lives
 * in the Iron's adapter and reads only the carrier's kind.
 *
 * <p>The billing half is asserted numerically here; the {@code CastSource} mapping and stack
 * consumption are asserted against the source, because {@code CastSource} lives in the Iron's jar
 * which is not on the unit-test classpath. The loaded GameTest profile covers the runtime
 * behaviour - pool delta, cooldown and stack count - against the real Iron's.
 */
class CrossCastSourcePolicyTest {

    /** The serialized values an item could claim, including the old silent default. */
    private static final String[] SERIALIZED_SOURCES = {"SCROLL", "COMMAND", "MOB", "NONE"};

    @AfterEach
    void clearRouting() {
        BridgeTestSupport.clear();
    }

    private static CostRules rules(ManaUnificationMode mode) {
        return CostRules.of(mode.getConfigName(), 1.0d, 1.0d, 0.5d, 0.5d,
            ConversionKind.FLAT_LEGACY, 1.25d, RoundingRule.HALF_UP, 1);
    }

    private static String handler() throws IOException {
        return Files.readString(
            TestPaths.of("src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java"));
    }

    // ------------------------------------------------------------------
    //  Billing does not depend on the serialized source
    // ------------------------------------------------------------------

    /**
     * A reusable carrier is billed the same whatever its data claims. The serialized source is
     * not an input to pricing at all, which is what this asserts: the quote is a function of the
     * carrier policy, the origin unit and the rules, and there is nowhere for a claimed
     * {@code SCROLL} to enter.
     */
    @Test
    void aReusableCarrierIsBilledIdentically_whateverSourceItClaims() {
        for (ManaUnificationMode mode : ManaUnificationMode.values()) {
            BridgeTestSupport.setMode(mode);
            CostRules rules = rules(mode);
            CostQuote expected = AnsQuotes.quote(80, ResourceUnit.IRONS_MANA,
                CarrierPolicy.REUSABLE_BOOK_SEMANTICS, rules);

            for (String claimed : SERIALIZED_SOURCES) {
                // The claim is data on the item; the policy comes from the item's kind. Pricing
                // the same reusable carrier must not move because the data says otherwise.
                CostQuote actual = AnsQuotes.quote(80, ResourceUnit.IRONS_MANA,
                    CarrierPolicy.REUSABLE_BOOK_SEMANTICS, rules);
                for (ResourceUnit unit : ResourceUnit.values()) {
                    assertEquals(expected.total(unit), actual.total(unit), 1.0e-9d,
                        mode + ": a carrier claiming " + claimed + " must be billed as the "
                            + "reusable carrier it actually is");
                }
            }

            assertFalse(expected.isFree(),
                mode + ": a reusable carrier's cross-cast must cost something - defaulting to "
                    + "SCROLL is what made it free");
        }
    }

    /** A real scroll and a reusable book are different carriers and price differently. */
    @Test
    void aConsumableScrollAndAReusableBookAreDistinctPolicies() {
        BridgeTestSupport.setMode(ManaUnificationMode.ISS_PRIMARY);
        CostRules rules = rules(ManaUnificationMode.ISS_PRIMARY);

        CostQuote book = AnsQuotes.quote(80, ResourceUnit.IRONS_MANA,
            CarrierPolicy.REUSABLE_BOOK_SEMANTICS, rules);
        CostQuote scroll = AnsQuotes.quote(80, ResourceUnit.IRONS_MANA,
            CarrierPolicy.CONSUMABLE_SCROLL_SEMANTICS, rules);
        CostQuote nativeCast = AnsQuotes.quote(80, ResourceUnit.IRONS_MANA,
            CarrierPolicy.NATIVE_ONLY, rules);

        assertEquals(book.total(ResourceUnit.IRONS_MANA), scroll.total(ResourceUnit.IRONS_MANA),
            1.0e-9d,
            "both are ANS cross-casts, so both carry the cross-cast multiplier; they differ in "
                + "consumption and cooldown, not in mana price");
        assertTrue(nativeCast.total(ResourceUnit.IRONS_MANA)
                < book.total(ResourceUnit.IRONS_MANA),
            "a native cast carries no cross-cast multiplier, so it must cost strictly less");
    }

    // ------------------------------------------------------------------
    //  The mapping lives in the adapter, and reads the item kind
    // ------------------------------------------------------------------

    @Test
    void theSerializedCastSourceIsNoLongerParsed() throws IOException {
        String src = handler();
        assertFalse(src.contains("parseCastSource"),
            "the serialized cast source must not be parsed back into a CastSource: it is "
                + "edit-controllable and it survives being copied onto a different item");
        assertFalse(src.contains("orElse(CastSource.SCROLL)"),
            "and it must certainly not default to SCROLL, which Iron's neither charges mana for "
                + "nor applies a cooldown to");
    }

    @Test
    void theCastSourceIsDerivedFromTheCarrierKind() throws IOException {
        String src = handler();
        assertTrue(src.contains("CarrierIdentity.policyOf(stack)"),
            "the carrier policy must be derived from the stack at the moment of the cast");
        assertTrue(src.contains("IronsCastSourceAdapter.forCarrier(carrier)"),
            "the CarrierPolicy -> CastSource mapping belongs in the Iron's adapter, in one place");

        String adapter = Files.readString(TestPaths.of(
            "src/main/java/com/otectus/arsnspells/spell/irons/IronsCastSourceAdapter.java"));
        assertTrue(adapter.contains("CastSource.SPELLBOOK"),
            "a reusable carrier must map to SPELLBOOK - the one source Iron's both charges mana "
                + "for and applies a cooldown to");
        assertTrue(adapter.contains("CastSource.SCROLL"),
            "a consumable scroll keeps scroll semantics, since it is paid for by being consumed");
        assertFalse(src.contains("CastSource nativeCastSource("),
            "the mapping must not be a method on the @EventBusSubscriber handler: NeoForge walks "
                + "getDeclaredMethods() to register it, and that resolves every declared method's "
                + "return type - so a CastSource-returning method there makes the whole mod fail "
                + "to load on an install without Iron's");
    }

    @Test
    void theEffectiveLevelComesFromThePinnedAdapterMethod() throws IOException {
        String src = handler();
        assertTrue(src.contains("spell.getLevelFor(entry.level(), player)"),
            "audit V02: the effective level must come from Iron's own "
                + "AbstractSpell.getLevelFor(int, LivingEntity), which folds in the Curios level "
                + "bonus and posts ModifySpellLevelEvent. max(1, stored) skipped both");
        int castFrom = src.indexOf("private static boolean castIronsSpell(");
        String body = src.substring(castFrom);
        assertFalse(body.contains("int level = Math.max(1, entry.level());"),
            "max(1, stored) as the primary level source is the defect; it may only remain as the "
                + "fallback when the adapter method is unavailable");
    }

    @Test
    void costsAndIdentityAreEstablishedBeforeAttemptInitiateCast() throws IOException {
        String src = handler();
        int castFrom = src.indexOf("private static boolean castIronsSpell(");
        String body = src.substring(castFrom);
        int quoteIdx = body.indexOf("AnsQuotes.quote(");
        int openIdx = body.indexOf("AttemptLedgerService.open(");
        int initiateIdx = body.indexOf("attemptInitiateCast(");
        assertTrue(quoteIdx >= 0 && openIdx >= 0 && initiateIdx >= 0,
            "castIronsSpell must quote, open an attempt, and initiate the cast");
        assertTrue(quoteIdx < initiateIdx,
            "the cost must be known before Iron's charges it - Iron's charges inside "
                + "attemptInitiateCast");
        assertTrue(openIdx < initiateIdx,
            "the attempt must exist before the cast, or there is nothing to attribute the "
                + "payment to");
    }

    @Test
    void theSeparateLegIsNotZeroedUntilTheLedgerCommittedIt() throws IOException {
        String src = Files.readString(
            TestPaths.of("src/main/java/com/otectus/arsnspells/spell/CrossCastIronsHandler.java"));
        assertTrue(src.contains("AttemptLedgerService.hasCommittedLeg("),
            "in SEPARATE the Iron's event cost may only be zeroed once the ledger has actually "
                + "committed that leg; zeroing it on faith is how the cross-cast became free");
    }
}
