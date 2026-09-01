package com.otectus.arsnspells.events;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-024 - accumulated school bonuses must be re-applied on respawn and on dimension
 * change, not only on login.
 *
 * <p>The bonuses are transient attribute modifiers derived from a persistent cast count, so
 * without this a player's +20% fire spell power after 200 fire casts vanished on death and
 * only rebuilt itself incrementally on the next fire cast.
 *
 * <p><b>Port divergence, deliberately pinned here.</b> The 1.20.1 line put both subscriptions
 * on {@code ProgressionHandler}. This port consolidates every per-player resync -  affinity,
 * cooldowns, resonance, progression, equipment - onto {@code CapabilityResyncHandler}, so the
 * subscription lives there and calls {@code ProgressionHandler.reapplyAll}. The invariant the
 * ticket cares about is that both events re-apply the bonuses; this asserts that, wherever the
 * subscription sits.
 */
class ProgressionReapplyHookTest {

    private static String read(String path) throws IOException {
        return Files.readString(TestPaths.of(path));
    }

    @Test
    void bonusReapplicationIsConsolidatedIntoOneHelper() throws IOException {
        String src = read("src/main/java/com/otectus/arsnspells/events/ProgressionHandler.java");
        assertTrue(src.contains("reapplyAll"),
            "the apply-all-bonuses logic must be one helper, not the iterate-and-apply loop "
                + "duplicated at every call site");
    }

    @Test
    void respawnAndDimensionChangeBothReapply() throws IOException {
        String resync =
            read("src/main/java/com/otectus/arsnspells/events/CapabilityResyncHandler.java");
        assertTrue(resync.contains("PlayerRespawnEvent"),
            "respawn must re-apply progression bonuses (ANS-HIGH-024)");
        assertTrue(resync.contains("PlayerChangedDimensionEvent"),
            "dimension change must re-apply progression bonuses (ANS-HIGH-024)");
        assertTrue(resync.contains("ProgressionHandler.reapplyAll"),
            "the resync path must actually call the progression helper, or both hooks "
                + "restore everything except the school bonuses");
    }
}
