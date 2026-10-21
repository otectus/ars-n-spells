package com.otectus.arsnspells.glyph.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Marks the thread while an imported glyph runs Iron's native code, so ANS's own listeners on
 * Iron's cast events (payment plans, cast lifecycle, category cooldowns, progression, affinity)
 * stand aside for that exact delegated invocation. Other mods' listeners still see Iron's native
 * events as they would for any cast. Always entered and exited in try/finally; never the only
 * record of future work.
 */
public final class GlyphInvocationScope {

    private static final ThreadLocal<Deque<UUID>> ACTIVE = ThreadLocal.withInitial(ArrayDeque::new);

    private GlyphInvocationScope() {
    }

    /** Whether an imported-glyph invocation is running on this thread. */
    public static boolean active() {
        return !ACTIVE.get().isEmpty();
    }

    public static void enter(UUID invocation) {
        ACTIVE.get().push(invocation);
    }

    public static void exit(UUID invocation) {
        Deque<UUID> stack = ACTIVE.get();
        if (!stack.isEmpty() && stack.peek().equals(invocation)) {
            stack.pop();
        } else {
            stack.remove(invocation);
        }
        if (stack.isEmpty()) ACTIVE.remove();
    }
}
