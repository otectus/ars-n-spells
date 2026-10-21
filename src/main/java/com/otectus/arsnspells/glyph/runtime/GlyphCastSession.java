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
 */
public final class GlyphCastSession {

    /** One imported occurrence of the root recipe. */
    public static final class Token {
        final int rootIndex;
        final ImportedGlyphKey key;
        @Nullable GlyphPayments.Reservation reservation;
        int executions;
        boolean cooldownCommitted;

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
    long expiresTick;
    final Map<Integer, Token> tokens = new LinkedHashMap<>();
    final Set<ResourceLocation> cooldownPending = new LinkedHashSet<>();
    final Deque<String> log = new ArrayDeque<>();
    int emissions;
    int pending;
    boolean launched;
    boolean rootFinished;
    boolean closed;
    GlyphOutcome lastOutcome;

    GlyphCastSession(net.minecraft.server.level.ServerPlayer owner, Spell rootSpell, CostRules rules,
                     int behaviorGeneration, long createdTick, long lifetimeTicks) {
        this.owner = owner.getUUID();
        this.ownerRef = new java.lang.ref.WeakReference<>(owner);
        this.dimension = owner.level().dimension();
        this.rootSpell = rootSpell;
        this.rules = rules;
        this.behaviorGeneration = behaviorGeneration;
        this.createdTick = createdTick;
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

    public int pending() {
        return pending;
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
