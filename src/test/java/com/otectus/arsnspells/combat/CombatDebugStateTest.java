package com.otectus.arsnspells.combat;

import com.otectus.arsnspells.TestPaths;
import com.otectus.arsnspells.config.AnsConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the two properties {@code /ans debug combat} depends on: the snapshot store keeps exactly
 * one entry per player per side and gives it up on eviction, and the recorders cost nothing while
 * debug mode is off.
 *
 * <p>The "debug on" half cannot be driven through {@code recordArs}/{@code recordIrons} here.
 * The gate is {@link AnsConfig#debugEnabled()}, a {@code ModConfigSpec} value that is never
 * loaded in a plain JUnit run, so it answers {@code false} for the whole test JVM and cannot be
 * set to anything else without a server. The map semantics are therefore exercised through the
 * package-private store methods, and the gate itself is pinned structurally - the same split the
 * other config-gated handler tests in this tree use.
 */
class CombatDebugStateTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @AfterEach
    void drain() {
        CombatDebugState.clearAll();
    }

    private static CombatDebugState.ArsSnapshot ars(float damage) {
        return new CombatDebugState.ArsSnapshot("ars_nouveau:glyph_harm", "Test", Set.of("fire"),
                                                damage, null, damage * 2.0f, false);
    }

    @Test
    void recordersAreNoOpsWhileDebugIsOff() {
        // The config spec is not loaded in a unit test, so debugEnabled() is false - which is
        // exactly the production "debug off" state this must be a no-op in.
        assertFalse(AnsConfig.debugEnabled(), "the unloaded config must read as debug-off");

        CombatDebugState.recordArs(null, null, null, 1.0f, 2.0f);
        CombatDebugState.recordIrons(null, "irons_spellbooks:fireball", "irons_spellbooks:fire",
                                     1.0f, 2.0f, 3.0f);

        assertNull(CombatDebugState.lastArs(PLAYER));
        assertNull(CombatDebugState.lastIrons(PLAYER));
    }

    @Test
    void theGateIsTheFirstStatementOfEachRecorder() throws IOException {
        // Structural, because the flag cannot be flipped in this JVM: the point of the gate is
        // that nothing is allocated ahead of it on a per-hit path, and that is a property of
        // statement order, not of a return value.
        // Normalised to LF: core.autocrlf is true in this repo, so the working tree carries
        // CRLF on Windows and LF elsewhere. Comparing raw text would make this assertion depend
        // on the checkout's line-ending policy rather than on the statement order it pins.
        String src = Files.readString(TestPaths.of(
            "src/main/java/com/otectus/arsnspells/combat/CombatDebugState.java"))
            .replace("\r\n", "\n");
        for (String method : new String[] {"public static void recordArs", "public static void recordIrons"}) {
            int start = src.indexOf(method);
            assertTrue(start > 0, method + " must exist");
            int body = src.indexOf('{', start);
            String firstStatement = src.substring(body + 1, src.indexOf(';', body)).trim();
            assertEquals("if (!AnsConfig.debugEnabled()) {\n            return", firstStatement,
                method + " must test the debug flag before it does anything else");
        }
    }

    @Test
    void eachPlayerKeepsOnlyTheMostRecentSnapshot() {
        CombatDebugState.storeArs(PLAYER, ars(1.0f));
        CombatDebugState.ArsSnapshot latest = ars(9.0f);
        CombatDebugState.storeArs(PLAYER, latest);

        assertSame(latest, CombatDebugState.lastArs(PLAYER),
            "the store is one entry per player, overwritten - not a history");
    }

    @Test
    void snapshotsAreKeyedPerPlayerAndPerSide() {
        CombatDebugState.storeArs(PLAYER, ars(1.0f));
        CombatDebugState.storeIrons(OTHER, new CombatDebugState.IronsSnapshot(
            "irons_spellbooks:fireball", "irons_spellbooks:fire", 5.0f, 2.0f, 7.0f));

        assertNull(CombatDebugState.lastArs(OTHER));
        assertNull(CombatDebugState.lastIrons(PLAYER));
        assertEquals(7.0f, CombatDebugState.lastIrons(OTHER).finalAmount());
    }

    @Test
    void clearDropsBothSidesForThatPlayerOnly() {
        CombatDebugState.storeArs(PLAYER, ars(1.0f));
        CombatDebugState.storeIrons(PLAYER, new CombatDebugState.IronsSnapshot(
            "irons_spellbooks:fireball", null, 5.0f, 2.0f, 7.0f));
        CombatDebugState.storeArs(OTHER, ars(3.0f));

        CombatDebugState.clear(PLAYER);

        assertNull(CombatDebugState.lastArs(PLAYER));
        assertNull(CombatDebugState.lastIrons(PLAYER));
        assertEquals(3.0f, CombatDebugState.lastArs(OTHER).rawDamage(),
            "logout eviction must not touch anyone else's snapshot");
    }

    @Test
    void clearAllDrainsEveryPlayer() {
        CombatDebugState.storeArs(PLAYER, ars(1.0f));
        CombatDebugState.storeIrons(OTHER, new CombatDebugState.IronsSnapshot(
            "irons_spellbooks:fireball", null, 5.0f, 2.0f, 7.0f));

        // Integrated server: the JVM survives world exit, so a leftover snapshot would be read
        // back by /ans debug combat in the next world.
        CombatDebugState.clearAll();

        assertNull(CombatDebugState.lastArs(PLAYER));
        assertNull(CombatDebugState.lastIrons(OTHER));
    }

    @Test
    void nullPlayerIdIsToleratedByEveryLookup() {
        assertNull(CombatDebugState.lastArs(null));
        assertNull(CombatDebugState.lastIrons(null));
        CombatDebugState.clear(null);
    }
}
