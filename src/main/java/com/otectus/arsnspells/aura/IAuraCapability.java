package com.otectus.arsnspells.aura;

import net.minecraft.nbt.CompoundTag;

/**
 * Capability interface for the aura resource system.
 * Aura is consumed instead of mana when wearing the Ring of Seven Virtues.
 */
public interface IAuraCapability {
    int getAura();
    int getMaxAura();
    void setAura(int amount);
    void setMaxAura(int amount);
    boolean consumeAura(int amount);
    void regenTick();
    void saveNBTData(CompoundTag tag);
    void loadNBTData(CompoundTag tag);
}
