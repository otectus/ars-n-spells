package com.otectus.arsnspells.glyph;

import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.CostRules;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One admitted Ars cast that contains imported Iron's glyphs.
 *
 * <p>The session is what makes a delayed delivery safe: it is stamped onto the cast's Ars
 * context, which Ars copies into every projectile, delay and child context the cast spawns, so
 * an effect that lands a minute later still finds the reservation that was taken for it, the
 * rules it was priced under and the emission budget it draws from, and never resolves against
 * whatever the caster happens to hold by then. It keeps no level or entity reference: the owner
 * is a UUID and is looked up live, so a session cannot keep a disconnected player alive.
 *
 * <p><b>Repeated casts (3.4.3).</b> A reservation does not keep a later cast of the same Iron's spell
 * out: admitting that cast hands back what this session still holds for the spell
 * ({@link GlyphSessions#releaseHoldsFor}), and this session's deliveries stay live and pay at their
 * effect boundary like any later execution. Only a charged or channelled child still blocks a second
 * cast of its spell ({@link #childPendingFor}), because Iron's has one cast slot. Iron's cooldown is
 * enforced at each execution's effect boundary instead, except for a spell in {@link #started}.
 *
 * <p>Server thread only.
 */
public final class GlyphCastSession {
    public enum State { OPEN, CLOSED }

    /** One imported glyph's position in the recipe, with the reservation its first execution redeems. */
    public static final class Occurrence {
        public final int index;
        public final ImportedGlyphDefinition definition;
        /** Base level after Amplify and Dampen, before Iron's equipment bonuses. */
        public final int augmentedLevel;
        /**
         * Iron's own level for {@link #augmentedLevel} ({@code getLevelFor}: Curios affinity and
         * {@code ModifySpellLevelEvent}), evaluated once at admission and frozen for every
         * execution of this occurrence, so the quote, the cast event and the effect all start
         * from the same level and the level event fires once per occurrence, not per lookup.
         */
        public final int nativeLevel;
        CastAttempt reservation;
        CostQuote quote;
        /** 3.4.1 review PAYMENT-01: which resource pays this occurrence; fixed at admission. */
        GlyphRoute route = GlyphRoute.MANA;
        /**
         * Audit ANS-001: the route native casting used for this caster at admission, which every
         * later execution must still be authorized for. Null for a free (creative or recast)
         * admission, which pays nothing on any route.
         */
        GlyphRoute admittedRoute;
        /** The first execution's LP leg, reserved at admission on the {@link GlyphRoute#LP} route. */
        java.util.UUID lpLeg;
        double lpReserved;
        /** LP route, insufficient LP, and the server kills instead of refusing (native death option). */
        boolean deathPenalty;
        boolean redeemed;
        int emissions;
        boolean childPending;

        Occurrence(int index, ImportedGlyphDefinition definition, int augmentedLevel, int nativeLevel) {
            this.index = index;
            this.definition = definition;
            this.augmentedLevel = augmentedLevel;
            this.nativeLevel = nativeLevel;
        }

        public boolean redeemed() { return redeemed; }
        public int emissions() { return emissions; }
        public CostQuote quote() { return quote; }
        public CastAttempt reservation() { return reservation; }
        public GlyphRoute route() { return route; }
        public GlyphRoute admittedRoute() { return admittedRoute; }
        public java.util.UUID lpLeg() { return lpLeg; }

        /** Whether this occurrence still holds an unredeemed first-execution payment, in any resource. */
        boolean holding() {
            if (redeemed) return false;
            return reservation != null && !reservation.state().isTerminal() || lpLeg != null;
        }
        public boolean childPending() { return childPending; }
    }

    final UUID id = UUID.randomUUID();
    final UUID owner;
    /** The live owner, held weakly: refunds need the player object, which must not be kept alive. */
    final java.lang.ref.WeakReference<net.minecraft.server.level.ServerPlayer> ownerRef;
    final ResourceKey<Level> dimension;
    final long createdTick;
    /** The lifetime bound this session was admitted with ({@link GlyphSessions#lifetimeTicks()}). */
    final long lifetimeTicks;
    long expiresTick;
    final CostRules rules;
    final int mappingGeneration;
    final GlyphInvocationBudget budget;
    final Map<Integer, Occurrence> occurrences = new LinkedHashMap<>();
    /**
     * 3.4.3: the Iron's spells this cast has begun executing. A cooldown on one of them is this cast's
     * own, so its later executions (a split, pierce, linger, wall, or the same glyph again in the recipe)
     * still run; any other cooldown refuses an execution before its effect. Per spell id, not per
     * occurrence: one cooldown covers every level and occurrence of a spell.
     */
    final Set<ResourceLocation> started = new HashSet<>();
    State state = State.OPEN;
    /** The Ars leg was committed: the root cast itself succeeded. */
    boolean rootCommitted;
    /** The root resolver entry has returned; with nothing pending the session is done. */
    boolean rootFinished;
    /** Ars projectiles and Ars delays carrying this session that have not finished. */
    int pending;
    String closeReason = "";

    GlyphCastSession(net.minecraft.server.level.ServerPlayer owner, ResourceKey<Level> dimension, long createdTick, long lifetimeTicks,
                     CostRules rules, int mappingGeneration, GlyphInvocationBudget budget) {
        this.owner = owner.getUUID();
        this.ownerRef = new java.lang.ref.WeakReference<>(owner);
        this.dimension = dimension;
        this.createdTick = createdTick;
        this.lifetimeTicks = lifetimeTicks;
        this.expiresTick = createdTick + lifetimeTicks;
        this.rules = rules;
        this.mappingGeneration = mappingGeneration;
        this.budget = budget;
    }

    public UUID id() { return id; }
    public UUID owner() { return owner; }
    public ResourceKey<Level> dimension() { return dimension; }
    public State state() { return state; }
    public CostRules rules() { return rules; }
    public GlyphInvocationBudget budget() { return budget; }
    public boolean rootCommitted() { return rootCommitted; }
    public long expiresTick() { return expiresTick; }
    public long lifetimeTicks() { return lifetimeTicks; }
    public String closeReason() { return closeReason; }
    public Map<Integer, Occurrence> occurrences() { return Collections.unmodifiableMap(occurrences); }

    public Occurrence occurrence(int recipeIndex) {
        return occurrences.get(recipeIndex);
    }

    /** Whether anything still needs this session: an unredeemed reservation or a charged child in flight. */
    boolean hasPendingWork() {
        for (Occurrence occurrence : occurrences.values()) {
            if (occurrence.childPending || occurrence.holding()) return true;
        }
        return false;
    }

    /** Whether a charged or channelled child of this session is still running or waiting. */
    boolean childPending() {
        for (Occurrence occurrence : occurrences.values()) if (occurrence.childPending) return true;
        return false;
    }

    public int pending() { return pending; }
    public boolean rootFinished() { return rootFinished; }

    /**
     * 3.4.3: whether a charged or channelled child of this cast is running in Iron's cast slot, or waiting
     * for it, for {@code spell}. That alone keeps a second cast of the spell out; an unredeemed reservation
     * no longer does.
     */
    boolean childPendingFor(ResourceLocation spell) {
        for (Occurrence occurrence : occurrences.values())
            if (occurrence.childPending && occurrence.definition.spell().equals(spell)) return true;
        return false;
    }

    /** Whether this cast has begun executing {@code spell}, so a cooldown on it is this cast's own (3.4.3). */
    public boolean started(ResourceLocation spell) {
        return started.contains(spell);
    }
}
