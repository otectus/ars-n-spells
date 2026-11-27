package com.otectus.arsnspells.inscription;

import com.otectus.arsnspells.contract.InscriptionPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Audit ANS-009: a matching server refusal beats the local plan, never for a different draft. */
class LoomFeedbackTest {
    private static final String OK = InscriptionPlan.REASON_OK;
    private static final String DRAFT = LoomFeedback.draft("rev", "map", "", "Name", "fire", "spell/heal");

    @Test
    void sharesTheServerRequestReasonCodes() {
        assertEquals(com.otectus.arsnspells.network.LoomRequestHandler.STALE, LoomFeedback.STALE);
        assertEquals(com.otectus.arsnspells.network.LoomRequestHandler.REJECTED, LoomFeedback.REJECTED);
    }

    @Test
    void aServerRefusalOfTheSameDraftIsShownOverALocalReady() {
        LoomFeedback feedback = new LoomFeedback();
        feedback.accept(LoomFeedback.INSCRIBE, "school unknown", DRAFT, 0);
        assertEquals("school unknown", feedback.inscribeDisplay(OK, DRAFT));
        assertEquals("school unknown", feedback.inscribeEnablement(OK, DRAFT), "a real refusal disables the action");
    }

    @Test
    void aRetryableRefusalIsShownButKeepsTheActionUsableAndRetriesOnce() {
        for (String reason : new String[]{LoomFeedback.REJECTED, LoomFeedback.STALE}) {
            LoomFeedback feedback = new LoomFeedback();
            feedback.accept(LoomFeedback.CONVERT, reason, DRAFT, 100);
            assertEquals(reason, feedback.inscribeDisplay(OK, DRAFT));
            assertEquals(OK, feedback.inscribeEnablement(OK, DRAFT), "a temporary refusal never disables the button");
            assertFalse(feedback.retryDue(DRAFT, 100 + LoomFeedback.RETRY_TICKS - 1));
            assertTrue(feedback.retryDue(DRAFT, 100 + LoomFeedback.RETRY_TICKS), "one fresh preview after the pause");
            assertFalse(feedback.retryDue(DRAFT, 100 + 5 * LoomFeedback.RETRY_TICKS), "and only one, not one per tick");
        }
    }

    @Test
    void refusalsOfEachActionFamilyStayInTheirFamily() {
        LoomFeedback feedback = new LoomFeedback();
        feedback.accept(LoomFeedback.SET_SCHOOL, "school unchanged", DRAFT, 0);
        assertEquals("school unchanged", feedback.schoolDisplay(OK, DRAFT));
        assertEquals(OK, feedback.inscribeDisplay(OK, DRAFT), "an Apply School refusal says nothing about Inscribe");
        feedback.accept(LoomFeedback.PREVIEW, "not blank", DRAFT, 0);
        assertEquals("not blank", feedback.inscribeDisplay(OK, DRAFT), "a preview answers for Inscribe and Convert");
        assertEquals(OK, feedback.schoolDisplay(OK, DRAFT));
    }

    @Test
    void anyDraftChangeMakesTheReplyStale() {
        LoomFeedback feedback = new LoomFeedback();
        feedback.accept(LoomFeedback.INSCRIBE, LoomFeedback.REJECTED, DRAFT, 0);
        for (String other : new String[]{
                LoomFeedback.draft("rev2", "map", "", "Name", "fire", "spell/heal"),
                LoomFeedback.draft("rev", "map2", "", "Name", "fire", "spell/heal"),
                LoomFeedback.draft("rev", "map", "irons_spellbooks:ice", "Name", "fire", "spell/heal"),
                LoomFeedback.draft("rev", "map", "", "Other", "fire", "spell/heal"),
                LoomFeedback.draft("rev", "map", "", "Name", "ice", "spell/heal"),
                LoomFeedback.draft("rev", "map", "", "Name", "fire", "spell/burst")}) {
            assertEquals(OK, feedback.inscribeDisplay(OK, other), "a reply never answers a different draft");
            assertFalse(feedback.retryDue(other, 1000));
        }
        assertEquals(DRAFT, LoomFeedback.draft("rev", "map", "", "  Name ", "fire", "spell/heal"), "the name is compared trimmed, as sent");
    }

    @Test
    void aSuccessLeavesTheLocalPlanInCharge() {
        LoomFeedback feedback = new LoomFeedback();
        feedback.accept(LoomFeedback.INSCRIBE, OK, DRAFT, 0);
        assertEquals("output full", feedback.inscribeDisplay("output full", DRAFT));
        feedback.accept(LoomFeedback.INSCRIBE, null, DRAFT, 0);
        assertEquals(OK, feedback.inscribeDisplay(OK, DRAFT));
        feedback.clear();
        assertEquals("x", feedback.inscribeDisplay("x", DRAFT));
    }
}
