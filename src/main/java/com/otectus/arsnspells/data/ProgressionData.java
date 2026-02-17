package com.otectus.arsnspells.data;

import net.minecraft.nbt.CompoundTag;

public class ProgressionData {
    private float magicExperience = 0;

    public float getXp() { return magicExperience; }
    public void addXp(float amt) { magicExperience += amt; }

    public void saveToNBT(CompoundTag nbt) {
        nbt.putFloat("BridgeXP", magicExperience);
    }

    public void loadFromNBT(CompoundTag nbt) {
        magicExperience = nbt.getFloat("BridgeXP");
    }
}