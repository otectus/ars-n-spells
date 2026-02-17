package com.otectus.arsnspells.aura;

import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.nbt.CompoundTag;

/**
 * Implementation of aura resource storage.
 * Aura regenerates passively and is consumed when casting spells with
 * the Ring of Seven Virtues equipped.
 */
public class AuraCapability implements IAuraCapability {
    private int aura;
    private int maxAura;
    private float regenAccumulator = 0f;

    public AuraCapability() {
        this.maxAura = AnsConfig.AURA_MAX_DEFAULT.get();
        this.aura = this.maxAura;
    }

    @Override
    public int getAura() {
        return aura;
    }

    @Override
    public int getMaxAura() {
        return maxAura;
    }

    @Override
    public void setAura(int amount) {
        this.aura = Math.max(0, Math.min(amount, maxAura));
    }

    @Override
    public void setMaxAura(int amount) {
        this.maxAura = Math.max(1, amount);
        if (this.aura > this.maxAura) {
            this.aura = this.maxAura;
        }
    }

    @Override
    public boolean consumeAura(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (this.aura < amount) {
            return false;
        }
        this.aura -= amount;
        return true;
    }

    @Override
    public void regenTick() {
        if (this.aura >= this.maxAura) {
            this.regenAccumulator = 0f;
            return;
        }
        float regenRate = AnsConfig.AURA_REGEN_RATE.get().floatValue();
        this.regenAccumulator += regenRate;
        if (this.regenAccumulator >= 1.0f) {
            int regen = (int) this.regenAccumulator;
            this.aura = Math.min(this.maxAura, this.aura + regen);
            this.regenAccumulator -= regen;
        }
    }

    @Override
    public void saveNBTData(CompoundTag tag) {
        tag.putInt("aura", this.aura);
        tag.putInt("maxAura", this.maxAura);
    }

    @Override
    public void loadNBTData(CompoundTag tag) {
        this.aura = tag.getInt("aura");
        this.maxAura = tag.getInt("maxAura");
        if (this.maxAura <= 0) {
            this.maxAura = AnsConfig.AURA_MAX_DEFAULT.get();
        }
        if (this.aura > this.maxAura) {
            this.aura = this.maxAura;
        }
    }
}
