package com.otectus.arsnspells.glyph;

import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.CostRules;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
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
        CastAttempt reservation;
        CostQuote quote;
        boolean redeemed;
        int emissions;
        boolean childPending;

        Occurrence(int index, ImportedGlyphDefinition definition, int augmentedLevel) {
            this.index = index;
            this.definition = definition;
            this.augmentedLevel = augmentedLevel;
        }

        public boolean redeemed() { return redeemed; }
        public int emissions() { return emissions; }
        public CostQuote quote() { return quote; }
        public CastAttempt reservation() { return reservation; }
        public boolean childPending() { return childPending; }
    }

    final UUID id = UUID.randomUUID();
    final UUID owner;
    /** The live owner, held weakly: refunds need the player object, which must not be kept alive. */
    final java.lang.ref.WeakReference<net.minecraft.server.level.ServerPlayer> ownerRef;
    final ResourceKey<Level> dimension;
    final long createdTick;
    final long expiresTick;
    final CostRules rules;
    final int mappingGeneration;
    final GlyphInvocationBudget budget;
    final Map<Integer, Occurrence> occurrences = new LinkedHashMap<>();
    State state = State.OPEN;
    /** The Ars leg was committed: the root cast itself succeeded. */
    boolean rootCommitted;
    String closeReason = "";

    GlyphCastSession(net.minecraft.server.level.ServerPlayer owner, ResourceKey<Level> dimension, long createdTick, long lifetimeTicks,
                     CostRules rules, int mappingGeneration, GlyphInvocationBudget budget) {
        this.owner = owner.getUUID();
        this.ownerRef = new java.lang.ref.WeakReference<>(owner);
        this.dimension = dimension;
        this.createdTick = createdTick;
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
    public String closeReason() { return closeReason; }
    public Map<Integer, Occurrence> occurrences() { return Collections.unmodifiableMap(occurrences); }

    public Occurrence occurrence(int recipeIndex) {
        return occurrences.get(recipeIndex);
    }

    /** Whether anything still needs this session: an unredeemed reservation or a charged child in flight. */
    boolean hasPendingWork() {
        for (Occurrence occurrence : occurrences.values()) {
            if (occurrence.childPending) return true;
            if (!occurrence.redeemed && occurrence.reservation != null && !occurrence.reservation.state().isTerminal()) return true;
        }
        return false;
    }

    /** Spell ids whose native cooldown is still pending in this session. */
    boolean pendingCooldownFor(ResourceLocation spell) {
        for (Occurrence occurrence : occurrences.values()) {
            if (occurrence.definition.spell().equals(spell) && (occurrence.childPending
                || (!occurrence.redeemed && occurrence.reservation != null && !occurrence.reservation.state().isTerminal())))
                return true;
        }
        return false;
    }
}
