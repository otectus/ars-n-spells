package com.otectus.arsnspells.glyph;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Charged and channelled imported glyphs running in a player's native Iron's cast slot.
 *
 * <p>At most one per player, because Iron's has one cast slot: a child is only started when the
 * slot is free (or, when the slot holds the outer native cast this very Ars cast runs inside, on
 * the next tick after it ends), and an unrelated cast in progress refuses the child rather than
 * being cancelled by it. The record carries the parent session, the Ars target and the
 * continuation, so Iron's own ticker can drive the windup, pulses, interruption and completion,
 * and ANS resumes or drops the rest of the Ars recipe from the outcome. Common code: no Iron's
 * type appears here.
 *
 * <p><b>Identity (3.4.1 review CAST-04).</b> Each child is started in the slot under its own
 * attempt tag ({@link Child#slotTag}, Iron's casting-equipment-slot string). A native cast is the
 * child only while it is being started by ANS ({@link #initiating}) or while the slot carries that
 * exact tag: a native cast of the same spell the player starts afterwards is never priced, targeted,
 * cancelled or completed as the child.
 */
public final class GlyphChildren {
    /** The prefix of every child's casting-slot tag. */
    public static final String SLOT_PREFIX = "ars_n_spells_glyph#";
    private static final AtomicLong ATTEMPTS = new AtomicLong();

    /** A child cast and everything needed to finish it. */
    public static final class Child {
        public final UUID owner;
        public final UUID sessionId;
        public final int recipeIndex;
        public final ResourceLocation spell;
        public final GlyphInvocation invocation;
        /** The casting-slot tag Iron's holds for exactly this attempt. */
        public final String slotTag;
        /** Iron's has started the cast in the slot. */
        public boolean started;
        /** At least one invocation reached its effect. */
        public boolean effectReached;
        /** The final invocation (a long cast's release, a channel's last pulse) reached its effect. */
        public boolean completed;
        /** {@link #finish} ran; it never runs twice. */
        public boolean finished;
        public int waitTicks;
        /** Started next tick by {@link #pendingStart}; null once started. */
        public Consumer<Child> pendingStart;
        /** Native invocations that reached their effect, what they paid, and the last effect's level (3.4.1 review DOCS-01). */
        public int effects;
        public String paid = "nothing";
        public int lastLevel = -1;

        /** One native invocation of this child reached its effect at {@code level}, having paid {@code legs}. */
        public void effectStarted(String legs, int level) {
            effects++;
            paid = effects == 1 ? legs : paid + ", " + legs;
            if (paid.length() > 160) paid = effects + " payments, last " + legs;
            lastLevel = level;
        }

        Child(GlyphInvocation invocation) {
            this.owner = invocation.caster().getUUID();
            this.sessionId = invocation.sessionId();
            this.recipeIndex = invocation.recipeIndex();
            this.spell = invocation.definition().spell();
            this.invocation = invocation;
            this.slotTag = SLOT_PREFIX + ATTEMPTS.incrementAndGet();
        }
    }

    private static final Map<UUID, Child> BY_OWNER = new HashMap<>();
    /** The child whose native cast ANS is starting right now, on the server thread. */
    private static Child initiating;

    private GlyphChildren() {}

    public static Child register(GlyphInvocation invocation) {
        Child child = new Child(invocation);
        BY_OWNER.put(child.owner, child);
        return child;
    }

    public static Child of(UUID owner) {
        return BY_OWNER.get(owner);
    }

    /** Mark {@code child} as the cast being started now; clear with null. */
    public static void initiating(Child child) {
        initiating = child;
    }

    /**
     * The child a native cast of {@code spell} in {@code castingSlot} belongs to: the child ANS is
     * starting right now, or a started child whose attempt tag the slot carries; otherwise null.
     */
    public static Child forNativeCast(UUID owner, ResourceLocation spell, String castingSlot) {
        Child starting = initiating;
        if (starting != null && starting.owner.equals(owner) && starting.spell.equals(spell)) return starting;
        Child child = BY_OWNER.get(owner);
        return child != null && child.started && child.spell.equals(spell) && child.slotTag.equals(castingSlot) ? child : null;
    }

    public static boolean busy(UUID owner) {
        return BY_OWNER.containsKey(owner);
    }

    /**
     * The cast ended: resume the Ars recipe when it completed, otherwise drop it, and release the
     * session's hold. The tail's pending reference is taken before the child's own pending flag is
     * cleared, so the session cannot close between them; a child with no tail closes its session at
     * once instead of leaving it idle until the lifetime bound. Idempotent.
     */
    public static void finish(Child child, boolean completed, MinecraftServer server) {
        if (child.finished) return;
        child.finished = true;
        receipt(child, completed ? "completed" : child.effects > 0 ? "interrupted_after_effect" : "cancelled_before_effect");
        if (BY_OWNER.get(child.owner) == child) BY_OWNER.remove(child.owner);
        GlyphCastSession session = GlyphSessions.byId(child.sessionId);
        child.invocation.payment().releaseIfUnpaid();
        if (completed && server != null && session != null) child.invocation.continuation().resume(session);
        else child.invocation.continuation().cancel();
        if (session != null) {
            GlyphCastSession.Occurrence occurrence = session.occurrence(child.recipeIndex);
            if (occurrence != null) occurrence.childPending = false;
            GlyphSessions.maybeClose(session);
        }
    }

    /**
     * The session closed while a child still belonged to it: drop the rest of the recipe and stop
     * the child's own cast in Iron's slot (only that attempt: the slot is identified by its tag), so
     * no native cast outlives its bookkeeping. Iron's own cancellation applies a continuous spell's
     * cooldown, so a closed session cannot erase the cooldown its paid pulses earned.
     */
    static void sessionClosed(GlyphCastSession session) {
        List<Child> stale = new ArrayList<>();
        for (Child child : BY_OWNER.values()) if (child.sessionId.equals(session.id())) stale.add(child);
        for (Child child : stale) {
            BY_OWNER.remove(child.owner);
            if (!child.finished) receipt(child, "session_closed " + session.closeReason());
            child.finished = true;
            child.invocation.continuation().cancel();
            if (child.started)
                com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridges.get().cancelChildCast(child.invocation.caster(), child.spell, child.slotTag);
        }
    }

    /**
     * The child's complete record for {@code /ans glyph last}: what each native invocation paid and the
     * level its effect ran at, replacing the bare "accepted, pending" the start recorded.
     */
    private static void receipt(Child child, String outcome) {
        var player = child.invocation.caster();
        if (player == null) return;
        GlyphInvocation invocation = child.invocation;
        GlyphSessions.record(player, new GlyphSessions.CastRecord(child.spell, invocation.definition().level(), invocation.augmentedLevel(),
            invocation.nativeLevel(), child.lastLevel, child.effects == 0 ? "nothing" : child.paid,
            outcome + (child.effects > 1 ? " (" + child.effects + " invocations)" : ""), "child", player.level().getGameTime()));
    }

    public static List<Child> all() {
        return new ArrayList<>(BY_OWNER.values());
    }

    public static void forget(UUID owner) {
        Child child = BY_OWNER.remove(owner);
        if (child != null) {
            child.finished = true;
            child.invocation.continuation().cancel();
        }
    }
}
