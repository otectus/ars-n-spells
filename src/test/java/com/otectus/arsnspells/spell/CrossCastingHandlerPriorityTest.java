package com.otectus.arsnspells.spell;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * ANS-CRIT-004 - {@code CrossCastingHandler.onArsSpellCost} must run at
 * {@code EventPriority.HIGHEST}, so the cross-cast multiplier applies to the unmodified base
 * cost before any other listener rewrites {@code event.currentCost}.
 *
 * <p>At default priority a listener that zeroes the cost first turns the documented 1.25x
 * premium into 0x1.25 - which is how the 1.20.1 line silently lost the premium entirely for
 * ring wearers.
 *
 * <p>A source-text assertion rather than reflection: loading {@link CrossCastingHandler}
 * pulls in Ars Nouveau API types from a jar that is not on the unit-test classpath.
 */
class CrossCastingHandlerPriorityTest {

    private static final Path SOURCE =
        TestPaths.of("src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java");

    @Test
    void onArsSpellCost_runsAtHighestPriority() {
        String source;
        try {
            source = Files.readString(SOURCE);
        } catch (IOException e) {
            fail("could not read CrossCastingHandler source at " + SOURCE + ": " + e);
            return;
        }

        int methodIdx = source.indexOf("public static void onArsSpellCost(");
        assertTrue(methodIdx > 0,
            "onArsSpellCost declaration not found - has it been renamed or removed?");

        int annotationIdx = source.lastIndexOf(
            "@SubscribeEvent(priority = EventPriority.HIGHEST)", methodIdx);
        assertTrue(annotationIdx > 0,
            "no @SubscribeEvent(priority = EventPriority.HIGHEST) precedes onArsSpellCost - "
                + "the ANS-CRIT-004 fix has regressed");

        // The annotation has to be THIS method's, not one further up the file. Anything
        // between them other than the javadoc means it belongs to a different handler.
        String between = source.substring(annotationIdx, methodIdx);
        assertTrue(!between.contains("public static void") && !between.contains("private static"),
            "the HIGHEST annotation must belong to onArsSpellCost, but another method "
                + "declaration sits between them");
    }
}
