package com.otectus.arsnspells.spell;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * ANS-CRIT-004 — verifies that {@code CrossCastingHandler.onArsSpellCost} is annotated
 * {@code @SubscribeEvent(priority = EventPriority.HIGHEST)} so it runs BEFORE the
 * {@code CursedRingHandler} / {@code VirtueRingHandler} handlers (also at HIGHEST).
 *
 * <p>Without this priority, the ring handlers would zero {@code event.currentCost} first,
 * and then the cross-cast multiplier would multiply 0×1.25 = 0, silently bypassing the
 * documented cross-cast overhead whenever a ring was worn.
 *
 * <p>Verifying via reflection requires loading {@link CrossCastingHandler}, whose
 * other methods reference Ars Nouveau API types (e.g. {@code SpellCostCalcEvent},
 * {@code SpellResolver}) that live in a deobf jar not present on the unit-test
 * classpath. So this test performs a textual assertion on the source file instead —
 * brittle but robust to the deobf gap.
 */
class CrossCastingHandlerPriorityTest {

    private static final Path SOURCE = Paths.get(
        "src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java");

    @Test
    void onArsSpellCost_runsAtHighestPriority_perSourceText() {
        String source;
        try {
            source = Files.readString(SOURCE);
        } catch (IOException e) {
            fail("Could not read CrossCastingHandler source at " + SOURCE.toAbsolutePath() + ": " + e);
            return;
        }

        int annotationIdx = source.indexOf("@SubscribeEvent(priority = EventPriority.HIGHEST)");
        int methodIdx = source.indexOf("public static void onArsSpellCost(");

        assertTrue(methodIdx > 0,
            "onArsSpellCost method declaration not found — has it been renamed or removed?");
        assertTrue(annotationIdx > 0,
            "No @SubscribeEvent(priority = EventPriority.HIGHEST) annotation found in "
                + "CrossCastingHandler.java — ANS-CRIT-004 fix has regressed.");
        // The HIGHEST-priority annotation must appear BEFORE the onArsSpellCost method
        // (i.e., it must be ITS annotation, not some other handler's).
        int methodFromAnnotation = source.indexOf("public static void onArsSpellCost(", annotationIdx);
        assertTrue(methodFromAnnotation > annotationIdx,
            "@SubscribeEvent(priority = EventPriority.HIGHEST) must annotate onArsSpellCost "
                + "(no other handler in this class should be HIGHEST). Order observed: "
                + "annotationIdx=" + annotationIdx + " methodIdx=" + methodFromAnnotation);
        // Within 200 characters of annotation: the method declaration should follow
        // (catches the case where a later method got the annotation instead).
        assertTrue(methodFromAnnotation - annotationIdx < 400,
            "Annotation @SubscribeEvent(priority = EventPriority.HIGHEST) is too far from "
                + "the onArsSpellCost declaration — likely not annotating it. Gap = "
                + (methodFromAnnotation - annotationIdx));
    }
}
