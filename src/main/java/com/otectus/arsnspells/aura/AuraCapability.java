package com.otectus.arsnspells.aura;

import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.nbt.CompoundTag;

/**
 * Implementation of aura resource storage.
 * Aura regenerates passively and is consumed when casting spells with
 * the Ring of Seven Virtues equipped.
 *
 * <p>Initialization is deferred until first access on the server tick so the config
 * is guaranteed to be loaded. Eagerly reading the config in the constructor races
 * with {@code AttachCapabilitiesEvent} firing before {@code ModConfigEvent.Loading},
 * which silently capped {@code maxAura} at the fallback value of 100 (10% of the
 * intended default of 1000). The fallback now only applies when the config really
 * isn't loaded yet AND we're not loading from NBT; once a configured max is read,
 * it's stamped onto the capability.
 */
public class AuraCapability implements IAuraCapability {
    /** Sentinel max from the pre-1.10.0 race that corrupted new players. */
    private static final int LEGACY_BROKEN_MAX = 100;
    private static final int HARD_FALLBACK_MAX = 1000;
    private static final String NBT_MIGRATED = "ans_v110_migrated";

    private int aura;
    private int maxAura;
    private boolean initialized = false;
    private boolean dirty = false;
    private float regenAccumulator = 0f;

    public AuraCapability() {
        // Defer config-driven initialization. Reading AURA_MAX_DEFAULT here races
        // with config load on first attach.
        this.maxAura = 0;
        this.aura = 0;
    }

    /**
     * Lazy-initialize state from config on first server-side access.
     * Safe to call repeatedly — only the first call has effect.
     */
    private void lazyInit() {
        if (initialized) {
            return;
        }
        int configMax = readConfigMax();
        this.maxAura = configMax;
        this.aura = configMax;
        this.initialized = true;
        this.dirty = true;
    }

    private static int readConfigMax() {
        try {
            return AnsConfig.AURA_MAX_DEFAULT.get();
        } catch (IllegalStateException e) {
            return HARD_FALLBACK_MAX;
        }
    }

    @Override
    public int getAura() {
        lazyInit();
        return aura;
    }

    @Override
    public int getMaxAura() {
        lazyInit();
        return maxAura;
    }

    @Override
    public void setAura(int amount) {
        lazyInit();
        int clamped = Math.max(0, Math.min(amount, maxAura));
        if (clamped != this.aura) {
            this.aura = clamped;
            this.dirty = true;
        }
    }

    @Override
    public void setMaxAura(int amount) {
        lazyInit();
        int newMax = Math.max(1, amount);
        if (newMax != this.maxAura) {
            this.maxAura = newMax;
            this.dirty = true;
        }
        if (this.aura > this.maxAura) {
            this.aura = this.maxAura;
            this.dirty = true;
        }
    }

    @Override
    public boolean consumeAura(int amount) {
        if (amount <= 0) {
            return true;
        }
        lazyInit();
        if (this.aura < amount) {
            return false;
        }
        this.aura -= amount;
        this.dirty = true;
        return true;
    }

    @Override
    public void regenTick() {
        lazyInit();
        if (this.aura >= this.maxAura) {
            this.regenAccumulator = 0f;
            return;
        }
        float regenRate = AnsConfig.AURA_REGEN_RATE.get().floatValue();
        this.regenAccumulator += regenRate;
        if (this.regenAccumulator >= 1.0f) {
            int regen = (int) this.regenAccumulator;
            this.aura = Math.min(this.maxAura, this.aura + regen);
            this.regenAccumulator = this.regenAccumulator % 1.0f; // Prevent float precision drift
            this.dirty = true;
        }
    }

    @Override
    public void saveNBTData(CompoundTag tag) {
        tag.putInt("aura", this.aura);
        tag.putInt("maxAura", this.maxAura);
        tag.putBoolean(NBT_MIGRATED, true);
    }

    @Override
    public void loadNBTData(CompoundTag tag) {
        int savedMax = tag.getInt("maxAura");
        int savedAura = tag.getInt("aura");
        boolean migrated = tag.getBoolean(NBT_MIGRATED);

        if (savedMax <= 0) {
            // Fresh capability with no prior state — defer to lazyInit on first access.
            this.maxAura = 0;
            this.aura = 0;
            this.initialized = false;
            this.dirty = true; // first sync should send the lazy-init result
            return;
        }

        int configMax = readConfigMax();
        if (!migrated && savedMax == LEGACY_BROKEN_MAX && configMax > LEGACY_BROKEN_MAX) {
            // One-shot migration: the pre-1.10.0 race capped this player at 100.
            // Restore them to the configured default and refill the pool.
            this.maxAura = configMax;
            this.aura = configMax;
            this.initialized = true;
            this.dirty = true;
            return;
        }

        this.maxAura = savedMax;
        this.aura = Math.min(savedAura, savedMax);
        this.initialized = true;
    }

    /**
     * @return whether state has changed since the last sync acknowledgement.
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Mark state as synced. Called by the capability provider after a sync packet
     * is dispatched.
     */
    public void clearDirty() {
        this.dirty = false;
    }
}
