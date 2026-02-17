package com.otectus.arsnspells.data;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModCapabilityProvider implements ICapabilitySerializable<CompoundTag> {
    private final AffinityData affinityData = new AffinityData();
    private final CooldownData cooldownData = new CooldownData();
    
    private final LazyOptional<AffinityData> affinityOptional = LazyOptional.of(() -> affinityData);
    private final LazyOptional<CooldownData> cooldownOptional = LazyOptional.of(() -> cooldownData);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == AffinityData.AFFINITY_DATA) return affinityOptional.cast();
        if (cap == CooldownData.COOLDOWN_CAP) return cooldownOptional.cast();
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        affinityData.saveToNBT(nbt);
        cooldownData.save(nbt);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        affinityData.loadFromNBT(nbt);
        cooldownData.load(nbt);
    }
}