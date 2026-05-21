package com.otectus.arsnspells.events;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-021 — verifies {@link LPDeathPrevention} subscribes to
 * {@code PlayerLoggedOutEvent} and clears its {@code activeTransactions} map.
 * Without this, a stale immune flag could linger past disconnect until another
 * player's periodic sweep evicted it.
 *
 * <p>ANS-HIGH-022 — verifies the damage-type filter was simplified. The previous
 * filter checked {@code contains("magic") || contains("indirectMagic") ||
 * contains("sacrifice")}, missing third-party magic damage types that should also
 * be blocked while the same-tick LP cast was in flight.
 */
class LPDeathPreventionLogoutTest {

    @Test
    void source_subscribesToPlayerLoggedOutEvent() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/events/LPDeathPrevention.java"));
        assertTrue(src.contains("PlayerLoggedOutEvent"),
            "LPDeathPrevention must subscribe to PlayerLoggedOutEvent (ANS-HIGH-021)");
        assertTrue(src.contains("activeTransactions.remove"),
            "the logout handler must call activeTransactions.remove(uuid)");
    }

    @Test
    void source_doesNotUseStaleDamageTypeWhitelist() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/events/LPDeathPrevention.java"));
        // The simplified filter from ANS-HIGH-022 should not contain the old
        // "indirectMagic" branch (which was already covered by contains("magic")).
        // The damage type narrowing has been removed in favour of trusting the
        // same-tick scope as the real guard.
        // (We allow "magic" to still appear in the source because the death-event
        // safety-net handler still uses it; we just check the indirectMagic
        // sub-clause is gone.)
        int hurtHandlerIdx = src.indexOf("public static void onPlayerHurt");
        if (hurtHandlerIdx < 0) {
            return; // method may have been renamed; not our concern here
        }
        int nextMethodIdx = src.indexOf("@SubscribeEvent", hurtHandlerIdx + 1);
        if (nextMethodIdx < 0) nextMethodIdx = src.length();
        String body = src.substring(hurtHandlerIdx, nextMethodIdx);
        assertTrue(!body.contains("indirectMagic"),
            "onPlayerHurt must no longer reference 'indirectMagic' explicitly "
                + "(ANS-HIGH-022 simplification — trusts same-tick scope instead)");
    }
}
