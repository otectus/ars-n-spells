package com.otectus.arsnspells.glyph;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 */
public final class GlyphChildren {
    /** A child cast and everything needed to finish it. */
    public static final class Child {
        public final UUID owner;
        public final UUID sessionId;
        public final int recipeIndex;
        public final ResourceLocation spell;
        public final GlyphInvocation invocation;
        /** Iron's has started the cast in the slot. */
        public boolean started;
        /** At least one invocation reached its effect. */
        public boolean effectReached;
        /** The final invocation (a long cast's release, a channel's last pulse) reached its effect. */
        public boolean completed;
        public int waitTicks;
        /** Started next tick by {@link #pendingStart}; null once started. */
        public Consumer<Child> pendingStart;

        Child(GlyphInvocation invocation) {
            this.owner = invocation.caster().getUUID();
            this.sessionId = invocation.sessionId();
            this.recipeIndex = invocation.recipeIndex();
            this.spell = invocation.definition().spell();
            this.invocation = invocation;
        }
    }

    private static final Map<UUID, Child> BY_OWNER = new HashMap<>();

    private GlyphChildren() {}

    public static Child register(GlyphInvocation invocation) {
        Child child = new Child(invocation);
        BY_OWNER.put(child.owner, child);
        return child;
    }

    public static Child of(UUID owner) {
        return BY_OWNER.get(owner);
    }

    /** The child running {@code spell} for {@code owner}, if that is what is in the owner's slot. */
    public static Child running(UUID owner, ResourceLocation spell) {
        Child child = BY_OWNER.get(owner);
        return child != null && child.spell.equals(spell) ? child : null;
    }

    public static boolean busy(UUID owner) {
        return BY_OWNER.containsKey(owner);
    }

    /** The cast ended: resume the Ars recipe when it completed, otherwise drop it, and release the session's hold. */
    public static void finish(Child child, boolean completed, MinecraftServer server) {
        if (BY_OWNER.get(child.owner) == child) BY_OWNER.remove(child.owner);
        GlyphCastSession session = GlyphSessions.byId(child.sessionId);
        if (session != null) {
            GlyphCastSession.Occurrence occurrence = session.occurrence(child.recipeIndex);
            if (occurrence != null) occurrence.childPending = false;
        }
        child.invocation.payment().releaseIfUnpaid();
        if (completed && server != null) child.invocation.continuation().resume(server);
        else child.invocation.continuation().cancel();
    }

    static void sessionClosed(GlyphCastSession session) {
        List<Child> stale = new ArrayList<>();
        for (Child child : BY_OWNER.values()) if (child.sessionId.equals(session.id())) stale.add(child);
        for (Child child : stale) {
            BY_OWNER.remove(child.owner);
            child.invocation.continuation().cancel();
        }
    }

    public static List<Child> all() {
        return new ArrayList<>(BY_OWNER.values());
    }

    public static void forget(UUID owner) {
        Child child = BY_OWNER.remove(owner);
        if (child != null) child.invocation.continuation().cancel();
    }
}
