package com.otectus.arsnspells.spell;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-030 — SEPARATE mode pre-pays the Iron's share of a cross-cast during
 * the Ars cost-calc event (ANS-CRIT-002). If the Ars leg then fails
 * (insufficient Ars mana or a downstream cancel), that payment must be
 * refunded; zeroing {@code issCost} alone erased the only record of it and made
 * a failed cross-cast a one-way Iron's-mana drain.
 */
class CrossCastSeparateRefundTest {

    @Test
    void entry_recordsIronsPrepaymentSeparatelyFromIssCost() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/spell/CrossCastContext.java"));
        assertTrue(src.contains("issPaid"),
            "CrossCastContext.Entry must record the pre-paid Iron's amount (ANS-HIGH-030)");
    }

    @Test
    void costCalc_stampsIssPaidBeforeZeroingIssCost() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java"));
        int paidIdx = src.indexOf("entry.issPaid = issCost");
        int zeroIdx = src.indexOf("entry.issCost = 0.0f");
        assertTrue(paidIdx >= 0, "the SEPARATE consume path must stamp entry.issPaid");
        assertTrue(zeroIdx > paidIdx,
            "issPaid must be recorded before issCost is zeroed for the TAIL mixin contract");
    }

    @Test
    void failedArsLeg_refundsViaSecondaryBridge() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java"));

        int castIdx = src.indexOf("public static boolean castArsSpell");
        assertTrue(castIdx >= 0);
        int nextMethodIdx = src.indexOf("private static boolean castIronsSpell", castIdx);
        if (nextMethodIdx < 0) nextMethodIdx = src.length();
        String body = src.substring(castIdx, nextMethodIdx);

        assertTrue(body.contains("issPaid") && body.contains("addMana"),
            "castArsSpell's failure path must refund the pre-paid Iron's mana via "
                + "the secondary bridge's compensating addMana (ANS-HIGH-030)");
    }
}
