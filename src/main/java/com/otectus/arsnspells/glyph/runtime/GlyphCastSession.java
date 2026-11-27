package com.otectus.arsnspells.glyph.runtime;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything one root Ars cast with imported glyphs is authorized to do, from admission until its
 * last projectile, delay, charge or channel is done.
 *
 * <p>Owned by {@link GlyphSessions} on the server thread. Holds an immutable copy of the root
 * recipe, the pricing rules and behaviour generation it was admitted under, and one
 * {@link Token} per imported occurrence: the reservation prepaid at admission for that
 * occurrence's first execution. Further executions of the same occurrence (split, pierce, area
 * fan-out) pay for themselves and count against the emission budget. Nothing here holds a world
 * or a player: entities are looked up by id when needed.
 *
 * <p><b>Repeated casts (3.4.3).</b> A prepaid reservation no longer keeps a later cast of the same
 * Iron's spell out: admitting that cast hands it back ({@link GlyphSessions}), and the occurrence's
 * delivery, if it still arrives, pays fresh at its effect boundary. Iron's cooldown is checked at
 * each execution instead, except for a spell in {@link #started}.
 */
public final class GlyphCastSession {

    /** One imported occurrence of the root recipe. */
    public static final class Token {
        final int rootIndex;
        final ImportedGlyphKey key;
        @Nullable GlyphPayments.Reservation reservation;
        int executions;
        /** The Ars level (Amplify/Dampen applied) admission saw, and Iron's level for it, evaluated once. */
        int effectiveLevel;
        int nativeLevel;

        Token(int rootIndex, ImportedGlyphKey key) {
            this.rootIndex = rootIndex;
            this.key = key;
        }

        public int executions() {
            return executions;
        }

        public boolean prepaid() {
            return reservation != null && !reservation.closed();
        }
    }

    final UUID id = UUID.randomUUID();
    final UUID owner;
    /** Weak: a session never keeps a disconnected player or its level alive. */
    final java.lang.ref.WeakReference<net.minecraft.server.level.ServerPlayer> ownerRef;
    final ResourceKey<Level> dimension;
    final Spell rootSpell;
    final CostRules rules;
    final int behaviorGeneration;
    final long createdTick;
    /** The lifetime bound this session was admitted with ({@link GlyphSessions#lifetimeTicks()}). */
    final long lifetimeTicks;
    long expiresTick;
    final Map<Integer, Token> tokens = new LinkedHashMap<>();
    /**
     * 3.4.3: the Iron's spells this cast has begun executing (an effect applied, or a charge or channel
     * started). A cooldown on one of them is this cast's own, so its later executions (a split, pierce,
     * area fan-out, or the same glyph again in the recipe) still run; any other cooldown refuses an
     * execution before its effect. Per spell id, not per occurrence: one cooldown covers every level.
     */
    final Set<ResourceLocation> started = new LinkedHashSet<>();
    final Deque<String> log = new ArrayDeque<>();
    int emissions;
    /**
     * Emission permits held by work already scheduled but not yet past its effect boundary (a charge
     * waiting for its cast time). 3.4.1 review CAST-05: {@code pendingEmissions + emissions} never
     * exceeds the budget, so a split or burst cannot schedule more charges than the cast may run.
     */
    int pendingEmissions;
    /** The emission budget agreed at admission; a reload can only tighten it, never widen it. */
    final int emissionLimit;
    int pending;
    boolean launched;
    boolean rootFinished;
    boolean closed;
    GlyphOutcome lastOutcome;

    GlyphCastSession(net.minecraft.server.level.ServerPlayer owner, Spell rootSpell, CostRules rules,
                     int behaviorGeneration, long createdTick, long lifetimeTicks, int emissionLimit) {
        this.emissionLimit = Math.max(1, emissionLimit);
        this.owner = owner.getUUID();
        this.ownerRef = new java.lang.ref.WeakReference<>(owner);
        this.dimension = owner.level().dimension();
        this.rootSpell = rootSpell;
        this.rules = rules;
        this.behaviorGeneration = behaviorGeneration;
        this.createdTick = createdTick;
        this.lifetimeTicks = lifetimeTicks;
        this.expiresTick = createdTick + lifetimeTicks;
    }

    public UUID id() {
        return id;
    }

    /** The owning player entity while it is still the live, connected one; null otherwise. */
    @Nullable
    public net.minecraft.server.level.ServerPlayer ownerEntity() {
        net.minecraft.server.level.ServerPlayer player = ownerRef.get();
        return player == null || player.isRemoved() || player.hasDisconnected() ? null : player;
    }

    boolean ownerGone() {
        return ownerEntity() == null;
    }

    public UUID owner() {
        return owner;
    }

    public int emissions() {
        return emissions;
    }

    public long lifetimeTicks() {
        return lifetimeTicks;
    }

    /** Whether this cast has begun executing {@code spell}, so a cooldown on it is this cast's own (3.4.3). */
    public boolean started(ResourceLocation spell) {
        return started.contains(spell);
    }

    /** Whether any occurrence still holds its prepaid reservation (3.4.3: what the session limit counts). */
    boolean holding() {
        for (Token token : tokens.values()) if (token.prepaid()) return true;
        return false;
    }

    public int pending() {
        return pending;
    }

    public int pendingEmissions() {
        return pendingEmissions;
    }

    /** The budget in force: the one agreed at admission, tightened by a lower current setting. */
    int emissionLimit(int current) {
        return Math.max(1, Math.min(emissionLimit, current));
    }

    /** Take a permit for one execution before scheduling it; false when the budget is spent. */
    boolean tryReserveEmission(int currentLimit) {
        if (emissions + pendingEmissions >= emissionLimit(currentLimit)) return false;
        pendingEmissions++;
        return true;
    }

    /** The execution reached its effect: its permit becomes a counted emission. */
    void consumeEmission() {
        if (pendingEmissions > 0) pendingEmissions--;
        emissions++;
    }

    /** The execution was refused or cancelled before any effect: give its permit back. */
    void releaseEmission() {
        if (pendingEmissions > 0) pendingEmissions--;
    }

    public boolean closed() {
        return closed;
    }

    @Nullable
    public GlyphOutcome lastOutcome() {
        return lastOutcome;
    }

    public List<Token> tokens() {
        return new ArrayList<>(tokens.values());
    }

    /** Held but unspent reservations, in their quoted legs, for diagnostics. */
    public List<GlyphPayments.Reservation> heldReservations() {
        List<GlyphPayments.Reservation> held = new ArrayList<>();
        for (Token token : tokens.values()) if (token.prepaid()) held.add(token.reservation);
        return held;
    }

    void record(String line) {
        if (log.size() >= 32) log.removeFirst();
        log.addLast(line);
    }

    public List<String> log() {
        return List.copyOf(log);
    }
}
