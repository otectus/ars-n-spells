package com.otectus.arsnspells.events;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-028 — verifies the death-event safety net in {@link LPDeathPrevention}
 * is scoped to the cast tick, like {@code onPlayerHurt}. The immune flag is not
 * cleared on successful casts (only the 1s TTL sweep or logout removes it), so a
 * death handler gated on {@code isLPImmune} alone cancelled ANY lethal
 * magic/sacrifice damage for up to ~3s after every Cursed-Ring cast — a
 * repeatable post-cast invulnerability exploit in safe mode.
 */
class LPDeathPreventionDeathTickScopeTest {

    @Test
    void deathHandler_enforcesSameTickScope() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/events/LPDeathPrevention.java"));

        int deathHandlerIdx = src.indexOf("public static void onPlayerDeath");
        assertTrue(deathHandlerIdx >= 0, "LPDeathPrevention must keep the onPlayerDeath safety net");

        int nextMethodIdx = src.indexOf("@SubscribeEvent", deathHandlerIdx + 1);
        if (nextMethodIdx < 0) nextMethodIdx = src.length();
        String body = src.substring(deathHandlerIdx, nextMethodIdx);

        assertTrue(body.contains("playerTickCount"),
            "onPlayerDeath must compare player.tickCount against the transaction's "
                + "playerTickCount (ANS-HIGH-028) — gating on isLPImmune alone leaves a "
                + "multi-second post-cast invulnerability window");
        assertTrue(!body.contains("if (!isLPImmune(player))"),
            "onPlayerDeath must not rely on the unscoped isLPImmune check (ANS-HIGH-028)");
    }
}
