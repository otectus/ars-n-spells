package com.otectus.arsnspells.mixin;

import com.otectus.arsnspells.mixin.ars.MixinManaCapability;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * ANS-HIGH-010 — verifies that {@code MixinManaCapability.arsnspells$inBridgeCall}
 * is now a {@code ThreadLocal<Set<UUID>>} instead of {@code ThreadLocal<Boolean>}.
 *
 * <p>The bug fixed: a global per-thread boolean meant ANY in-flight bridge call
 * blocked all other players' ManaCap reads on the same thread, so AoE / party-share
 * effects that walked other players' mana silently bypassed the bridge.
 */
class MixinManaCapabilityPerUuidGuardTest {

    @Test
    void inBridgeCall_isThreadLocalOfPerPlayerDirectionSets() throws Exception {
        // ANS-HIGH-010 made the guard per player. Audit V06 additionally made it per
        // direction: a write routed to the shared pool used to block the read the very same
        // call needs to report its result, so the mutation fell through to native ManaCap.
        Field f = MixinManaCapability.class.getDeclaredField("arsnspells$inBridgeCall");
        f.setAccessible(true);
        Object value = f.get(null);
        assertNotNull(value);
        assertTrue(value instanceof ThreadLocal,
            "guard field must remain a ThreadLocal");
        Object initial = ((ThreadLocal<?>) value).get();
        assertNotNull(initial, "initialValue must produce a non-null Map");
        assertTrue(initial instanceof Map,
            "guard must hold a Map<UUID, EnumSet<Direction>> (ANS-HIGH-010 + V06); was "
                + initial.getClass().getName());
        assertTrue(((Map<?, ?>) initial).isEmpty(), "initial Map must be empty");
    }

    @Test
    void source_referencesEnterAndExitGuardHelpers() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/mixin/ars/MixinManaCapability.java"));
        assertTrue(src.contains("arsnspells$enterGuard"),
            "the source must define an enterGuard helper for per-UUID guard entry");
        assertTrue(src.contains("arsnspells$exitGuard"),
            "the source must define an exitGuard helper for per-UUID guard exit");
        // ANS-HIGH-010: the old boolean .set(true)/.set(false) pattern should be gone.
        // Find any naked `.set(true)` or `.set(false)` on the guard field.
        assertFalse(src.contains("arsnspells$inBridgeCall.set(true)"),
            "old global-boolean .set(true) pattern must be gone (ANS-HIGH-010)");
        assertFalse(src.contains("arsnspells$inBridgeCall.set(false)"),
            "old global-boolean .set(false) pattern must be gone (ANS-HIGH-010)");
    }

    @Test
    void source_clearsThreadLocalWhenSetEmptyToPreventLeak() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/mixin/ars/MixinManaCapability.java"));
        // ANS-HIGH-010 also adds the "remove the ThreadLocal when set is empty" guard
        // to avoid the canonical ThreadLocal-leak antipattern on long-lived threads.
        if (!src.contains("arsnspells$inBridgeCall.remove()")) {
            fail("the per-UUID guard must call ThreadLocal.remove() when the set goes empty "
                + "(ANS-HIGH-010) — preserves the no-leak property on long-lived threads");
        }
    }
}
