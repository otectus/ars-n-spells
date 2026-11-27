package com.otectus.arsnspells.glyph;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * The imported-glyph execution running on this thread, as Covenant of the Seven's native Iron's
 * handlers must see it (audit ANS-001).
 *
 * <p>An imported execution posts Iron's own {@code SpellPreCastEvent} and {@code SpellOnCastEvent},
 * which Covenant's Virtue Ring handlers answer from the caster's equipment at that moment. While a
 * frame is open for a caster:
 * <ul>
 *   <li>on a {@link GlyphRoute#MANA} or {@link GlyphRoute#LP} route, Covenant's aura debit and its
 *       zeroing of the Iron's cost are suppressed, so a Virtue Ring equipped after admission can
 *       neither take aura nor make the child free;</li>
 *   <li>on the {@link GlyphRoute#AURA_NATIVE} route Covenant runs natively, and reaching its aura
 *       drain records a receipt; an execution without one is refused, never marked paid.</li>
 * </ul>
 * Outside a frame ordinary native Iron's casting is untouched. Frames are opened and closed by the
 * Iron's invoker around the events it posts, always in {@code finally}.
 */
public final class ImportedExecutionScope {
    /** One open execution: whose, on which route, and whether Covenant charged its aura. */
    public static final class Frame {
        private final UUID player;
        private final GlyphRoute route;
        private boolean auraCharged;

        private Frame(UUID player, GlyphRoute route) {
            this.player = player;
            this.route = route;
        }

        public GlyphRoute route() { return route; }
        public boolean auraCharged() { return auraCharged; }
    }

    private static final ThreadLocal<Deque<Frame>> ACTIVE = ThreadLocal.withInitial(ArrayDeque::new);

    private ImportedExecutionScope() {}

    public static Frame push(UUID player, GlyphRoute route) {
        Frame frame = new Frame(player, route == null ? GlyphRoute.MANA : route);
        ACTIVE.get().push(frame);
        return frame;
    }

    public static void pop(@Nullable Frame frame) {
        if (frame == null) return;
        Deque<Frame> stack = ACTIVE.get();
        if (stack.peek() == frame) stack.pop();
        else stack.remove(frame);
        if (stack.isEmpty()) ACTIVE.remove();
    }

    /** The innermost open frame for {@code player}, or null when no imported execution of theirs is running. */
    @Nullable
    public static Frame current(@Nullable Player player) {
        if (player == null) return null;
        for (Frame frame : ACTIVE.get()) if (frame.player.equals(player.getUUID())) return frame;
        return null;
    }

    /** Whether Covenant's native aura handlers must stand aside for {@code player}'s current imported execution. */
    public static boolean suppressesNativeAura(@Nullable Player player) {
        Frame frame = current(player);
        return frame != null && frame.route != GlyphRoute.AURA_NATIVE;
    }

    /** Covenant reached its aura drain for {@code player}: record the receipt on the open aura frame. */
    public static void recordAuraCharged(@Nullable Player player) {
        Frame frame = current(player);
        if (frame != null && frame.route == GlyphRoute.AURA_NATIVE) frame.auraCharged = true;
    }
}
