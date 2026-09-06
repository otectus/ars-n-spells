package com.otectus.arsnspells.spell;

import com.otectus.arsnspells.TestPaths;
import com.otectus.arsnspells.bridge.BridgeTestSupport;
import com.otectus.arsnspells.casting.AnsQuotes;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.AttemptLedger;
import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.ConversionKind;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.contract.RoundingRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit V01 - a cost query must be repeatable, and must charge nothing.
 *
 * <p>The audit scenario, exactly: a spell whose native cost is 100, under a cross-cast multiplier
 * of 2. The first query must answer 200 and so must the second.
 *
 * <p>Ars 5.13.1 asks more than once per cast, and asks with a <em>fresh event object</em> each
 * time: {@code SpellResolver.getResolveCost()} constructs and posts a
 * {@code SpellCostCalcEvent.Pre}, {@code getExpendedCost()} constructs and posts a
 * {@code SpellCostCalcEvent.Post}, and a listener registered on the common supertype receives
 * both. The old handler guarded itself with {@code entry.tryMarkMultiplierApplied()}, a
 * once-per-<em>attempt</em> latch. That does not make the pricing idempotent; it makes the second
 * event <b>unpriced</b>, so the second query returned the bare 100. The first query also pre-paid
 * the Iron's leg, which meant merely asking what a spell cost moved mana.
 *
 * <p>{@link #twoFreshEvents_bothGetTheFullPrice()} states the requirement and
 * {@link #theOldOncePerAttemptLatchIsWhatBrokeIt()} demonstrates the failure mode it replaced, so
 * the regression is legible without the defect being present.
 *
 * <p><b>Why a stand-in event.</b> {@code SpellCostCalcEvent} lives in the Ars Nouveau jar, which
 * is not on the unit-test classpath - loading {@link CrossCastingHandler} here would fail before
 * any assertion ran. The stand-in below is a fresh mutable {@code currentCost} holder, which is
 * all the real event is at this seam ({@code public int currentCost}); the pricing it is fed is
 * the production path, not a copy. The real event type is covered by the loaded GameTest profile.
 */
class CostQuoteRepeatabilityTest {

    /** What a {@code SpellCostCalcEvent} is at this seam: a fresh, mutable cost field. */
    private static final class FreshCostEvent {
        int currentCost;

        FreshCostEvent(int nativeCost) {
            this.currentCost = nativeCost;
        }
    }

    private static final UUID PLAYER = UUID.fromString("0a000000-0000-0000-0000-00000000c057");

    /** The audit's numbers. */
    private static final int NATIVE_COST = 100;
    private static final double MULTIPLIER = 2.0d;

    private final AttemptLedger ledger = new AttemptLedger();

    @AfterEach
    void clearRouting() {
        BridgeTestSupport.clear();
    }

    private static CostRules rules() {
        return CostRules.of(ManaUnificationMode.ARS_PRIMARY.getConfigName(), 1.0d, 1.0d,
            0.5d, 0.5d, ConversionKind.FLAT_LEGACY, MULTIPLIER, RoundingRule.HALF_UP, 1);
    }

    private CastAttempt openQuotedAttempt() {
        BridgeTestSupport.setMode(ManaUnificationMode.ARS_PRIMARY);
        CostQuote quote = AnsQuotes.quote(NATIVE_COST, ResourceUnit.ARS_MANA,
            CarrierPolicy.REUSABLE_BOOK_SEMANTICS, rules());
        CastAttempt attempt = ledger.open(PLAYER, "carrier#0", 1, quote, 0L);
        attempt.validate();
        attempt.markQuoted();
        return attempt;
    }

    /**
     * The production shape: look up the open attempt's quote and apply it to whatever event
     * object arrived. Applying an immutable quote to a fresh event is idempotent by construction,
     * which is the whole reason the quote exists.
     */
    private static void priceFromQuote(FreshCostEvent event, CastAttempt attempt) {
        event.currentCost = AnsQuotes.legAsInt(attempt.quote(), ResourceUnit.ARS_MANA, rules());
    }

    @Test
    void twoFreshEvents_bothGetTheFullPrice() {
        CastAttempt attempt = openQuotedAttempt();

        FreshCostEvent first = new FreshCostEvent(NATIVE_COST);
        priceFromQuote(first, attempt);
        assertEquals(200, first.currentCost,
            "the first query must price the cast at base x multiplier");

        FreshCostEvent second = new FreshCostEvent(NATIVE_COST);
        priceFromQuote(second, attempt);
        assertEquals(200, second.currentCost,
            "the second query must answer identically. Ars builds a fresh cost event per query, "
                + "so an answer that depends on how many times it has been asked is not a price");

        for (int i = 0; i < 10; i++) {
            FreshCostEvent again = new FreshCostEvent(NATIVE_COST);
            priceFromQuote(again, attempt);
            assertEquals(200, again.currentCost, "query " + i + " must answer identically too");
        }
    }

    /** Ten reads must leave the attempt exactly where it was: quoted, and holding nothing. */
    @Test
    void repeatedQueries_moveNoResources() {
        CastAttempt attempt = openQuotedAttempt();
        for (int i = 0; i < 10; i++) {
            priceFromQuote(new FreshCostEvent(NATIVE_COST), attempt);
        }
        assertTrue(attempt.reservedLegs().isEmpty(),
            "a cost query must not reserve - asking the price is not paying it");
        assertTrue(attempt.paidLegs().isEmpty(), "nor pay");
        assertFalse(attempt.isReleased(), "nor settle the attempt");
        assertEquals(1, ledger.openCount(), "the attempt is still open and still quoted");
    }

    /**
     * The defect, reproduced, so the fix has something to be a fix of. A once-per-attempt latch
     * prices the first event and abandons every one after it.
     */
    @Test
    void theOldOncePerAttemptLatchIsWhatBrokeIt() {
        AtomicBoolean multiplierApplied = new AtomicBoolean(false);
        CastAttempt attempt = openQuotedAttempt();

        FreshCostEvent first = new FreshCostEvent(NATIVE_COST);
        if (multiplierApplied.compareAndSet(false, true)) {
            priceFromQuote(first, attempt);
        }
        assertEquals(200, first.currentCost, "the latch lets the first event through");

        FreshCostEvent second = new FreshCostEvent(NATIVE_COST);
        if (multiplierApplied.compareAndSet(false, true)) {
            priceFromQuote(second, attempt);
        }
        assertEquals(NATIVE_COST, second.currentCost,
            "and drops the second on the floor at its unmultiplied native cost - this is the "
                + "V01 defect, and it is why idempotence must be per event, not per attempt");
    }

    /** The handler must not have regrown a per-attempt latch. */
    @Test
    void theHandlerPricesEveryEvent() throws IOException {
        String src = Files.readString(
            TestPaths.of("src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java"));
        int from = src.indexOf("public static void onArsSpellCost(");
        int to = src.indexOf("private static void logDebug(", from);
        String body = src.substring(from, to < 0 ? src.length() : to);

        assertFalse(body.contains("tryMarkMultiplierApplied"),
            "a once-per-attempt latch here unprices every event after the first");
        assertTrue(body.contains("event.currentCost = AnsQuotes.legAsInt"),
            "every event this handler sees must be priced from the quote, unconditionally");
    }
}
