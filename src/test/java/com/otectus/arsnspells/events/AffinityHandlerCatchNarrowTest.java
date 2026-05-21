package com.otectus.arsnspells.events;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-023 — verifies that {@link AffinityHandler#onSpellCast} narrowed its
 * catch from broad {@code Exception} to {@code IllegalArgumentException}, so
 * packet-send failures no longer silently disappear.
 */
class AffinityHandlerCatchNarrowTest {

    @Test
    void source_narrowsCatchToIllegalArgumentException() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/events/AffinityHandler.java"));
        assertTrue(src.contains("catch (IllegalArgumentException"),
            "AffinityHandler.onSpellCast must catch only IllegalArgumentException (ANS-HIGH-023)");
        assertFalse(src.contains("catch (Exception ignored)"),
            "the broad catch(Exception ignored) pattern must be gone after ANS-HIGH-023");
    }
}
