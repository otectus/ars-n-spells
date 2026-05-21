package com.otectus.arsnspells.events;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-024 — verifies that {@link ProgressionHandler} subscribes to both
 * {@code PlayerRespawnEvent} and {@code PlayerChangedDimensionEvent} to re-apply
 * transient school bonuses.
 *
 * <p>Without this, a player's accumulated school bonuses (e.g. +20% fire spell
 * power after 200 fire casts) vanished on death until their next fire cast
 * incrementally rebuilt the modifier.
 */
class ProgressionHandlerRespawnHookTest {

    @Test
    void source_handlesRespawnAndDimensionChange() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/events/ProgressionHandler.java"));
        assertTrue(src.contains("onPlayerRespawn") || src.contains("PlayerRespawnEvent"),
            "ProgressionHandler must subscribe to PlayerRespawnEvent (ANS-HIGH-024)");
        assertTrue(src.contains("onPlayerChangedDimension")
                || src.contains("PlayerChangedDimensionEvent"),
            "ProgressionHandler must subscribe to PlayerChangedDimensionEvent (ANS-HIGH-024)");
    }

    @Test
    void source_consolidatesBonusReapplicationLogic() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/events/ProgressionHandler.java"));
        // ANS-HIGH-024 should consolidate the apply-all-bonuses logic so login,
        // respawn and dim change all call the same helper rather than duplicating
        // the iterate-and-apply loop in three places.
        assertTrue(src.contains("reapplyAllBonuses"),
            "the apply-all-bonuses logic should be consolidated into a helper "
                + "(reapplyAllBonuses) called from each event handler");
    }
}
