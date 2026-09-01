package com.otectus.arsnspells.spell;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-030 - SEPARATE mode pre-pays the Iron's share of a cross-cast during the Ars
 * cost-calc event (ANS-CRIT-002). If the Ars leg then fails - insufficient Ars mana, or a
 * downstream cancel - that payment must be refunded. Zeroing {@code issCost} alone erased the
 * only record of it and turned a failed cross-cast into a one-way Iron's-mana drain.
 */
class CrossCastSeparateRefundTest {

    private static String read(String path) throws IOException {
        return Files.readString(TestPaths.of(path));
    }

    @Test
    void entry_recordsIronsPrepaymentSeparatelyFromIssCost() throws IOException {
        assertTrue(read("src/main/java/com/otectus/arsnspells/spell/CrossCastContext.java")
                .contains("issPaid"),
            "CrossCastContext.Entry must record the pre-paid Iron's amount (ANS-HIGH-030)");
    }

    @Test
    void costCalc_stampsIssPaidBeforeZeroingIssCost() throws IOException {
        String src = read("src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java");
        int paidIdx = src.indexOf("entry.issPaid = issCost");
        int zeroIdx = src.indexOf("entry.issCost = 0.0f");
        assertTrue(paidIdx >= 0, "the SEPARATE consume path must stamp entry.issPaid");
        assertTrue(zeroIdx > paidIdx,
            "issPaid must be recorded before issCost is zeroed for the TAIL mixin contract - "
                + "the other order loses the record of the payment");
    }

    @Test
    void failedArsLeg_refundsViaSecondaryBridge() throws IOException {
        String src = read("src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java");
        int castIdx = src.indexOf("public static boolean castArsSpell");
        assertTrue(castIdx >= 0, "castArsSpell must exist");
        int nextMethodIdx = src.indexOf("private static boolean castIronsSpell", castIdx);
        String body = src.substring(castIdx, nextMethodIdx < 0 ? src.length() : nextMethodIdx);
        assertTrue(body.contains("refundPrepaidIronsShare") || body.contains("issPaid"),
            "castArsSpell's failure path must refund the pre-paid Iron's mana (ANS-HIGH-030)");
    }

    @Test
    void theRefundIsACompensatingAdd_notASnapshotRestore() throws IOException {
        String src = read("src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java");
        int refundIdx = src.indexOf("refundPrepaidIronsShare");
        assertTrue(refundIdx >= 0, "there must be a named refund path");
        String body = src.substring(src.indexOf("private static", refundIdx) >= 0
            ? src.indexOf("private static void refundPrepaidIronsShare") : refundIdx);
        int end = body.indexOf("\n    }");
        body = end > 0 ? body.substring(0, end) : body;
        assertTrue(body.contains("addMana"),
            "the refund must be a compensating addMana (ANS-CRIT-003): a snapshot-and-restore "
                + "clobbers any regen or buff mana that landed while the cast was in flight");
    }
}
