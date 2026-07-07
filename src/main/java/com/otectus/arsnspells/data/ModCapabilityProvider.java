package com.otectus.arsnspells.data;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class ModCapabilityProvider implements ICapabilitySerializable<CompoundTag> {

    /**
     * Audit E3: schema version stamped into every serialized capability tag.
     * Readers currently accept anything (all shipped schemas are version <= 1
     * and absence means "pre-3.0.2"); the field exists so a future format change
     * has something to branch on instead of silently misreading old keys.
     * Bump this ONLY together with explicit migration logic in deserializeNBT.
     */
    public static final int DATA_VERSION = 1;
    private static final String DATA_VERSION_KEY = "AnsDataVersion";

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
        nbt.putInt(DATA_VERSION_KEY, DATA_VERSION);
        affinityData.saveToNBT(nbt);
        cooldownData.save(nbt);
        progressionData.saveToNBT(nbt);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        // Version 0 (key absent, pre-3.0.2) and 1 share the same layout — no
        // migration needed yet. When DATA_VERSION is bumped, branch here on
        // nbt.getInt(DATA_VERSION_KEY) BEFORE handing the tag to the data classes.
        affinityData.loadFromNBT(nbt);
        cooldownData.load(nbt);
        progressionData.loadFromNBT(nbt);
    }

    /**
     * Persist affinity and progression data across death/dimension change.
     * Cooldown data resets on death (clone hook does not copy it) but IS persisted
     * across server save/restart via {@link #serializeNBT()}.
     *
     * <p>ANS-HIGH-008: registered at HIGHEST priority so capability copy completes
     * BEFORE any third-party {@code PlayerEvent.Clone} handler at default priority
     * reads our caps. Without this, a HIGHEST-priority reader from another mod
     * would see the freshly-default state.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
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
