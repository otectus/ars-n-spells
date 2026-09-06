package com.otectus.arsnspells.spell;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-030, restated for the attempt ledger.
 *
 * <p>The original defect: SEPARATE mode pre-paid the Iron's share of a cross-cast during the Ars
 * cost-calc event, and if the Ars leg then failed - insufficient mana, or a downstream cancel -
 * nothing gave it back, so a failed cross-cast became a one-way Iron's drain.
 *
 * <p>Audit V01 removed the prepayment rather than patching the refund: a cost <em>query</em> must
 * not move mana at all, and Ars queries the cost several times per cast. The invariant these
 * tests now hold is stronger and simpler - <b>the cost-calc handler charges nothing</b>, payment
 * is a ledger reservation taken once, and every exit path settles it exactly once through a
 * {@code finally}. Release is idempotent inside {@link com.otectus.arsnspells.contract.AttemptLedger},
 * so the double-refund the old two-path cleanup could produce is now unrepresentable.
 *
 * <p>Source-text assertions rather than reflection: loading {@link CrossCastingHandler} pulls in
 * Ars Nouveau API types from a jar that is not on the unit-test classpath.
 */
class CrossCastSeparateRefundTest {

    private static String handler() throws IOException {
        return Files.readString(
            TestPaths.of("src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java"));
    }

    private static String costCalcBody(String src) {
        int from = src.indexOf("public static void onArsSpellCost(");
        int to = src.indexOf("private static void logDebug(", from);
        return src.substring(from, to < 0 ? src.length() : to);
    }

    @Test
    void costCalcHandler_neverMovesMana() throws IOException {
        String body = costCalcBody(handler());
        assertFalse(body.contains("consumeMana"),
            "the cost-calc handler must not consume mana: Ars posts a fresh cost event on every "
                + "query, so charging here means merely asking what a spell costs pays for it "
                + "(audit V01)");
        assertFalse(body.contains("addMana"),
            "nor credit it - a handler that neither charges nor refunds needs no refund path");
    }

    @Test
    void costCalcHandler_readsTheOpenAttemptsQuote() throws IOException {
        String body = costCalcBody(handler());
        assertTrue(body.contains("AttemptLedgerService.findOpen"),
            "a repeated cost query must answer from the attempt's existing quote, not re-derive "
                + "a new price each time");
        assertTrue(body.contains("CastAttempt::quote") || body.contains(".quote()"),
            "the answer is the attempt's quote");
    }

    @Test
    void castArsSpell_settlesEveryExitPathInAFinally() throws IOException {
        String src = handler();
        int castIdx = src.indexOf("public static boolean castArsSpell(Player player, ItemStack stack, CrossModSpell entry, UUID attemptId)");
        assertTrue(castIdx >= 0, "castArsSpell must exist");
        int nextIdx = src.indexOf("private static void refundPrepaidIronsShare", castIdx);
        String body = src.substring(castIdx, nextIdx < 0 ? src.length() : nextIdx);

        assertTrue(body.contains("} finally {"),
            "failure cleanup must be centralised in a finally - the old code released on some "
                + "paths and not others, which is how an interrupted long cast could both "
                + "refund and complete");
        assertTrue(body.contains("AttemptLedgerService.fail"),
            "the failure path must release the reservation through the ledger, whose release is "
                + "one-shot");
        assertTrue(body.contains("AttemptLedgerService.complete"),
            "the success path must settle rather than release");
    }

    @Test
    void castArsSpell_usesTheActualOnCastResult() throws IOException {
        String src = handler();
        assertTrue(src.contains("success = resolver.onCast(stack, player.level())"),
            "audit V03: SpellResolver.onCast(ItemStack, Level) returns boolean in the pinned Ars "
                + "5.13.1.1400, and that value is the cast's success. Discarding it and "
                + "returning true reported success after a failed or cancelled cast");
    }

    @Test
    void theCancelObserver_subscribesWithReceiveCanceled() throws IOException {
        String src = handler();
        int idx = src.indexOf("public static void onArsSpellCastFailed(");
        assertTrue(idx >= 0, "the cancellation observer must still exist as defence in depth");
        String preceding = src.substring(Math.max(0, idx - 400), idx);
        assertTrue(preceding.contains("receiveCanceled = true"),
            "a listener that does not opt in never sees a cancelled event, so the observer "
                + "written to catch cancellations never fired for them");
    }

    @Test
    void theRefundIsACompensatingAdd_notASnapshotRestore() throws IOException {
        String src = handler();
        int refundIdx = src.indexOf("private static void refundPrepaidIronsShare");
        assertTrue(refundIdx >= 0, "there must be a named compensating path");
        String body = src.substring(refundIdx);
        int end = body.indexOf("\n    }");
        body = end > 0 ? body.substring(0, end) : body;
        assertTrue(body.contains("addMana"),
            "the compensation must be a relative addMana (ANS-CRIT-003): a snapshot-and-restore "
                + "clobbers any regen or buff mana that landed while the cast was in flight");
        assertFalse(body.contains("setMana"),
            "a snapshot-and-restore is exactly what ANS-CRIT-003 was filed against");
    }
}
