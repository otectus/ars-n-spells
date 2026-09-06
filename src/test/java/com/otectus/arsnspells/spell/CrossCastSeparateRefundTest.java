package com.otectus.arsnspells.spell;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit V01 - the Ars cost handler must not move mana, and the refund must not be hand-rolled.
 *
 * <p>Supersedes the ANS-HIGH-030 version of this test, which asserted the opposite: that the
 * SEPARATE-mode Iron's share was pre-consumed inside {@code onArsSpellCost} and that
 * {@code castArsSpell}'s failure path refunded it by hand from {@code entry.issPaid}. Both
 * halves were remediation of an earlier finding, and both are now the finding. Consuming
 * inside cost calculation means asking the price costs money - and upstream Ars 4.12.7 asks
 * the price on every {@code getResolveCost()} call, from both {@code canCast()} and
 * {@code expendMana()}. A hand-rolled refund keyed on a mutable float field is not tied to the
 * charge it reverses, so two exit paths could each pay it back.
 *
 * <p>Both jobs belong to the ledger now: the quote is reserved once at the pre-cast gate and
 * released exactly once, through {@code CastAttempt.tryMarkReleased}.
 */
class CrossCastSeparateRefundTest {

    private static String handlerSource() throws IOException {
        return Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java"));
    }

    /** The quoting method the cost-calc handler delegates to, plus the handler itself. */
    private static String costPathSource() throws IOException {
        String src = handlerSource();
        int start = src.indexOf("public static int quoteArsCostForEvent");
        assertTrue(start >= 0, "quoteArsCostForEvent must exist - it is the cost path");
        int end = src.indexOf("public static void onPlayerTick", start);
        if (end < 0) {
            end = src.length();
        }
        return src.substring(start, end);
    }

    @Test
    void theCostPathNeverMovesMana() throws IOException {
        String costPath = costPathSource();
        for (String mover : new String[]{"consumeMana", "addMana", "consumeManaForMode",
                "consumeQuote", "CastLedger.reserve"}) {
            assertFalse(costPath.contains(mover),
                "the Ars cost path must not call " + mover + ": a cost query is asked an "
                    + "unbounded number of times per cast and must be free of side effects");
        }
    }

    @Test
    void thePrepaymentRecordIsGone() throws IOException {
        assertFalse(handlerSource().contains("entry.issPaid"),
            "nothing is pre-paid during cost calculation any more, so there is no prepayment "
                + "to record");
    }

    @Test
    void theFailurePathReleasesThroughTheLedger() throws IOException {
        String src = handlerSource();
        int castIdx = src.indexOf("public static boolean castArsSpell");
        assertTrue(castIdx >= 0);
        int nextMethodIdx = src.indexOf("private static boolean castIronsSpell", castIdx);
        if (nextMethodIdx < 0) {
            nextMethodIdx = src.length();
        }
        String body = src.substring(castIdx, nextMethodIdx);

        assertTrue(body.contains("CastLedger.cancel"),
            "a failed cross-cast must release its reservation through the ledger, which "
                + "refunds exactly once, rather than crediting a bridge by hand");
        assertFalse(body.contains("issPaid"),
            "the hand-rolled prepayment refund must be gone");
    }
}
