package com.otectus.arsnspells.data;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class ModCapabilityProvider implements ICapabilitySerializable<CompoundTag> {
    private final AffinityData affinityData = new AffinityData();
    private final CooldownData cooldownData = new CooldownData();
    private final ProgressionData progressionData = new ProgressionData();

    private final LazyOptional<AffinityData> affinityOptional = LazyOptional.of(() -> affinityData);
    private final LazyOptional<CooldownData> cooldownOptional = LazyOptional.of(() -> cooldownData);
    private final LazyOptional<ProgressionData> progressionOptional = LazyOptional.of(() -> progressionData);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == AffinityData.AFFINITY_DATA) return affinityOptional.cast();
        if (cap == CooldownData.COOLDOWN_CAP) return cooldownOptional.cast();
        if (cap == ProgressionData.PROGRESSION_DATA) return progressionOptional.cast();
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        affinityData.saveToNBT(nbt);
        cooldownData.save(nbt);
        progressionData.saveToNBT(nbt);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        affinityData.loadFromNBT(nbt);
        cooldownData.load(nbt);
        progressionData.loadFromNBT(nbt);
    }

    /**
     * Persist affinity and progression data across death/dimension change.
     * Cooldown data intentionally NOT persisted (resets on death).
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(AffinityData.AFFINITY_DATA).ifPresent(oldData -> {
            event.getEntity().getCapability(AffinityData.AFFINITY_DATA).ifPresent(newData -> {
                CompoundTag tag = new CompoundTag();
                oldData.saveToNBT(tag);
                newData.loadFromNBT(tag);
            });
        });
        event.getOriginal().getCapability(ProgressionData.PROGRESSION_DATA).ifPresent(oldData -> {
            event.getEntity().getCapability(ProgressionData.PROGRESSION_DATA).ifPresent(newData -> {
                CompoundTag tag = new CompoundTag();
                oldData.saveToNBT(tag);
                newData.loadFromNBT(tag);
            });
        });
        event.getOriginal().invalidateCaps();
    }
}
