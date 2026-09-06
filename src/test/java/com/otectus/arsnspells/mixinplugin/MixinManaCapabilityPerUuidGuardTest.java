package com.otectus.arsnspells.mixinplugin;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * ANS-HIGH-010 - {@code MixinManaCapability}'s recursion guard must be keyed per player.
 *
 * <p>The bug: a thread-global {@code ThreadLocal<Boolean>} meant that while ANY player's
 * bridge call was in flight, ManaCap interception was suppressed for EVERY player on that
 * thread - so AoE and party-share effects that walk another player's mana silently bypassed
 * the bridge and read native Ars data. The recursion the guard actually defends against is
 * always same-player, so keying it by UUID makes it exact.
 *
 * <p>Audit V06 moved the guard itself into {@link com.otectus.arsnspells.bridge.ManaMutationRouter}
 * and gave it a second dimension - a <b>direction</b> - so the mixin and the routing share one
 * notion of "in flight". The per-player property this class was written for is unchanged and is
 * asserted at its new home; the mixin is still checked for the shape of its use.
 *
 * <p>Read off the source rather than by reflecting on the class: {@code MixinManaCapability}
 * is a mixin, and loading one outside the transformer is not something to rely on in a unit
 * test. The 1.20.1 version reflected on the field; here the structural form is checked
 * instead, which also catches a half-migration that keeps the per-player key but reintroduces a
 * global check.
 */
class MixinManaCapabilityPerUuidGuardTest {

    private static final Path SOURCE =
        TestPaths.of("src/main/java/com/otectus/arsnspells/mixin/ars/MixinManaCapability.java");

    private static final Path ROUTER =
        TestPaths.of("src/main/java/com/otectus/arsnspells/bridge/ManaMutationRouter.java");

    private static String source() throws IOException {
        return Files.readString(SOURCE);
    }

    private static String router() throws IOException {
        return Files.readString(ROUTER);
    }

    @Test
    void guardIsKeyedPerPlayer_notThreadGlobal() throws IOException {
        String src = router();
        assertTrue(src.contains("ThreadLocal<Map<UUID, EnumSet<Direction>>> ACTIVE"),
            "the guard must be keyed by player (ANS-HIGH-010) and by direction (audit V06), not "
                + "by a thread-global flag");
        assertFalse(src.contains("ThreadLocal<Boolean>"),
            "the thread-global boolean guard is the regressed form");
        assertFalse(source().contains("ThreadLocal<Boolean> arsnspells$inBridgeCall"),
            "and it must not have grown back inside the mixin either");
    }

    @Test
    void guardDistinguishesReadsFromWrites() throws IOException {
        String src = router();
        assertTrue(src.contains("READ") && src.contains("WRITE"),
            "audit V06: routing a write reads the pool back to report the new balance, and a "
                + "single flag per player made that read look like recursion and drop through to "
                + "stale native data. A read may nest inside a write");
    }

    @Test
    void source_referencesEnterAndExitGuardHelpers() throws IOException {
        String src = source();
        assertTrue(src.contains("arsnspells$enterGuard"),
            "the source must define an enterGuard helper for per-UUID guard entry");
        assertTrue(src.contains("arsnspells$exitGuard"),
            "the source must define an exitGuard helper for per-UUID guard exit");
        assertTrue(src.contains("arsnspells$isGuarded"),
            "the source must define a per-player guard check");
        // The old global-boolean pattern must be gone entirely.
        assertFalse(src.contains("arsnspells$inBridgeCall.set(true)"),
            "old global-boolean .set(true) pattern must be gone (ANS-HIGH-010)");
        assertFalse(src.contains("arsnspells$inBridgeCall.set(false)"),
            "old global-boolean .set(false) pattern must be gone (ANS-HIGH-010)");
    }

    @Test
    void everyGuardEntryHasAMatchingExit() throws IOException {
        String src = source();
        int enters = countOccurrences(src, "arsnspells$enterGuard(player);");
        int exits = countOccurrences(src, "arsnspells$exitGuard(player);");
        assertTrue(enters > 0, "expected the guard to be entered somewhere");
        if (enters != exits) {
            fail("every guard entry needs a matching exit or the player stays permanently "
                + "guarded on that thread: " + enters + " enter(s), " + exits + " exit(s)");
        }
    }

    @Test
    void guardIsCheckedAfterThePlayerIsResolved() throws IOException {
        // The check has to come after the instanceof that produces `player` - a per-player
        // guard cannot be consulted before there is a player to key on. Getting this
        // backwards is how a mechanical migration from the boolean form fails.
        String src = source();
        int firstCheck = src.indexOf("arsnspells$isGuarded(player)");
        int firstPlayerBinding = src.indexOf("this.entity instanceof Player player");
        assertTrue(firstCheck > firstPlayerBinding && firstPlayerBinding > 0,
            "the per-player guard check must follow the player binding it keys on");
    }

    @Test
    void source_clearsThreadLocalWhenSetEmptyToPreventLeak() throws IOException {
        // ANS-HIGH-010 also keeps the "remove the ThreadLocal when nothing is in flight" guard,
        // avoiding the canonical ThreadLocal-leak antipattern on long-lived server threads.
        if (!router().contains("ACTIVE.remove()")) {
            fail("the guard must call ThreadLocal.remove() when nothing is in flight "
                + "(ANS-HIGH-010) - preserves the no-leak property on long-lived threads");
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }
}
