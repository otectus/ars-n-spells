package com.otectus.arsnspells.spell;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public final class CrossCastContext {
    // ANS-OPT-006: 200 -> 100 ticks (5 seconds at 20 TPS) so the cross-cast context
    // TTL aligns with CursedRingHandler.PENDING_COST_TTL_TICKS and
    // VirtueRingHandler.PENDING_COST_TTL_TICKS. Faster eviction = less stale-state
    // hazard if anything in the pipeline forgets to clear() on its own.
    private static final long DEFAULT_TTL_TICKS = 100L;
    private static final Map<UUID, Entry> ACTIVE_CASTS = new ConcurrentHashMap<>();
    private static final ThreadLocal<ManaCheckOverride> MANA_CHECK_OVERRIDE = new ThreadLocal<>();

    private CrossCastContext() {
    }

    public static void begin(Player player, CrossSpellType type, long gameTime) {
        beginWithAttempt(player, type, gameTime, UUID.randomUUID());
    }

    public static void beginWithAttempt(Player player, CrossSpellType type, long gameTime, UUID attemptId) {
        if (player == null) {
            return;
        }
        ACTIVE_CASTS.put(player.getUUID(), new Entry(type, gameTime + DEFAULT_TTL_TICKS, attemptId));
    }

    public static void begin(Player player, CrossSpellType type, long gameTime, float arsCost, float issCost) {
        begin(player, type, gameTime, arsCost, issCost, null, UUID.randomUUID());
    }

    public static void begin(Player player, CrossSpellType type, long gameTime, float arsCost, float issCost,
        String spellId) {
        begin(player, type, gameTime, arsCost, issCost, spellId, UUID.randomUUID());
    }

    public static void begin(Player player, CrossSpellType type, long gameTime, float arsCost, float issCost,
        String spellId, UUID attemptId) {
        if (player == null) {
            return;
        }
        Entry entry = new Entry(type, gameTime + DEFAULT_TTL_TICKS, attemptId);
        entry.arsCost = arsCost;
        entry.issCost = issCost;
        entry.costsReady = true;
        entry.spellId = spellId;
        ACTIVE_CASTS.put(player.getUUID(), entry);
    }

    public static Entry peek(Player player) {
        if (player == null) {
            return null;
        }
        Entry entry = ACTIVE_CASTS.get(player.getUUID());
        if (entry != null && entry.isExpired(player.level().getGameTime())) {
            ACTIVE_CASTS.remove(player.getUUID());
            return null;
        }
        return entry;
    }

    public static Entry take(Player player) {
        if (player == null) {
            return null;
        }
        Entry entry = ACTIVE_CASTS.remove(player.getUUID());
        if (entry != null && entry.isExpired(player.level().getGameTime())) {
            return null;
        }
        return entry;
    }

    public static void clear(Player player) {
        if (player != null) {
            ACTIVE_CASTS.remove(player.getUUID());
        }
    }

    public static void cleanupExpired(Player player, long gameTime) {
        if (player == null) {
            return;
        }
        Entry entry = ACTIVE_CASTS.get(player.getUUID());
        if (entry != null && entry.isExpired(gameTime)) {
            ACTIVE_CASTS.remove(player.getUUID());
        }
    }

    public static boolean withManaCheckOverride(Player player, float issPercent, BooleanSupplier action) {
        if (player == null) {
            return action.getAsBoolean();
        }
        if (Math.abs(issPercent - 1.0f) < 1.0e-4f) {
            return action.getAsBoolean();
        }
        ManaCheckOverride previous = MANA_CHECK_OVERRIDE.get();
        MANA_CHECK_OVERRIDE.set(new ManaCheckOverride(player.getUUID(), issPercent));
        try {
            return action.getAsBoolean();
        } finally {
            if (previous == null) {
                MANA_CHECK_OVERRIDE.remove();
            } else {
                MANA_CHECK_OVERRIDE.set(previous);
            }
        }
    }

    public static ManaCheckOverride getManaCheckOverride(Player player) {
        if (player == null) {
            return null;
        }
        ManaCheckOverride override = MANA_CHECK_OVERRIDE.get();
        if (override == null) {
            return null;
        }
        return override.playerId.equals(player.getUUID()) ? override : null;
    }

    public static final class Entry {
        public final CrossSpellType type;
        /**
         * Per-attempt UUID used to correlate trace logs across the cross-cast
         * pipeline (client packet send -> server receive -> validate ->
         * resource check -> upstream cast -> effect). Always non-null; the
         * Phase 2 packet path generates a server-side UUID, while non-packet
         * callers default to a random UUID.
         */
        public final UUID attemptId;
        private final long expiresAt;
        // ANS-HIGH-004: volatile so writes from the cost-calc event are visible to the
        // TAIL mixin (which may run on a different thread under exotic mod chains) and
        // to concurrent peek() readers.
        public volatile float arsCost;
        public volatile float issCost;
        public volatile boolean costsReady;
        public volatile boolean blocked;
        public volatile String spellId;
        /**
         * ANS-HIGH-004: one-shot guard via AtomicBoolean. The Ars cost-calc event can
         * fire more than once during a resolve (preview vs. actual deduction). The
         * previous boolean read-then-write was racy under overlapping cross-casts;
         * {@link #tryMarkMultiplierApplied()} uses compareAndSet so exactly one
         * caller wins the first-application slot.
         */
        private final java.util.concurrent.atomic.AtomicBoolean multiplierApplied =
            new java.util.concurrent.atomic.AtomicBoolean(false);

        private Entry(CrossSpellType type, long expiresAt, UUID attemptId) {
            this.type = type;
            this.expiresAt = expiresAt;
            this.attemptId = attemptId != null ? attemptId : UUID.randomUUID();
        }

        public boolean isExpired(long gameTime) {
            return gameTime >= expiresAt;
        }

        /** Atomic check-and-mark. Returns true iff this is the first caller. */
        public boolean tryMarkMultiplierApplied() {
            return multiplierApplied.compareAndSet(false, true);
        }

        public boolean isMultiplierApplied() {
            return multiplierApplied.get();
        }
    }

    public static final class ManaCheckOverride {
        public final UUID playerId;
        public final float issPercent;

        private ManaCheckOverride(UUID playerId, float issPercent) {
            this.playerId = playerId;
            this.issPercent = issPercent;
        }

        public boolean isUnlimited() {
            return issPercent <= 0.0f;
        }
    }
}
