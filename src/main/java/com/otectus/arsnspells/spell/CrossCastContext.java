package com.otectus.arsnspells.spell;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public final class CrossCastContext {
    private static final long DEFAULT_TTL_TICKS = 200L;
    private static final Map<UUID, Entry> ACTIVE_CASTS = new ConcurrentHashMap<>();
    private static final ThreadLocal<ManaCheckOverride> MANA_CHECK_OVERRIDE = new ThreadLocal<>();

    private CrossCastContext() {
    }

    public static void begin(Player player, CrossSpellType type, long gameTime) {
        if (player == null) {
            return;
        }
        ACTIVE_CASTS.put(player.getUUID(), new Entry(type, gameTime + DEFAULT_TTL_TICKS));
    }

    public static void begin(Player player, CrossSpellType type, long gameTime, float arsCost, float issCost) {
        if (player == null) {
            return;
        }
        Entry entry = new Entry(type, gameTime + DEFAULT_TTL_TICKS);
        entry.arsCost = arsCost;
        entry.issCost = issCost;
        entry.costsReady = true;
        ACTIVE_CASTS.put(player.getUUID(), entry);
    }

    public static void begin(Player player, CrossSpellType type, long gameTime, float arsCost, float issCost,
        String spellId) {
        if (player == null) {
            return;
        }
        Entry entry = new Entry(type, gameTime + DEFAULT_TTL_TICKS);
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
        private final long expiresAt;
        public float arsCost;
        public float issCost;
        public boolean costsReady;
        public boolean blocked;
        public String spellId;

        private Entry(CrossSpellType type, long expiresAt) {
            this.type = type;
            this.expiresAt = expiresAt;
        }

        public boolean isExpired(long gameTime) {
            return gameTime >= expiresAt;
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
