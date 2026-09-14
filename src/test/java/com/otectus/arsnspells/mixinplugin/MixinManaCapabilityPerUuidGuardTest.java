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
 * <p>Read off the source rather than by reflecting on the class: {@code MixinManaCapability}
 * is a mixin, and loading one outside the transformer is not something to rely on in a unit
 * test. The 1.20.1 version reflected on the field; here the structural form is checked
 * instead, which also catches a half-migration that keeps the {@code Set} but reintroduces a
 * global check.
 */
class MixinManaCapabilityPerUuidGuardTest {

    private static final Path SOURCE =
        TestPaths.of("src/main/java/com/otectus/arsnspells/mixin/ars/MixinManaCapability.java");

    private static String source() throws IOException {
        return Files.readString(SOURCE);
    }

    @Test
    void guardHoldsASetOfUuids_notABoolean() throws IOException {
        String src = source();
        assertTrue(src.contains("ThreadLocal<Set<UUID>> arsnspells$inBridgeCall"),
            "the guard must hold a Set<UUID> (ANS-HIGH-010), not a thread-global flag");
        assertFalse(src.contains("ThreadLocal<Boolean> arsnspells$inBridgeCall"),
            "the thread-global boolean guard is the regressed form");
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
        // ANS-HIGH-010 also keeps the "remove the ThreadLocal when the set goes empty" guard,
        // avoiding the canonical ThreadLocal-leak antipattern on long-lived server threads.
        if (!source().contains("arsnspells$inBridgeCall.remove()")) {
            fail("the per-UUID guard must call ThreadLocal.remove() when the set goes empty "
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
