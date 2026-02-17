package com.otectus.arsnspells.data;

import com.otectus.arsnspells.affinity.AffinityType;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import java.util.HashMap;
import java.util.Map;

public class AffinityData {
    public static final Capability<AffinityData> AFFINITY_DATA = CapabilityManager.get(new CapabilityToken<>() {});

    private final Map<AffinityType, Integer> levels = new HashMap<>();

    public int getLevel(AffinityType type) {
        return levels.getOrDefault(type, 0);
    }

    public void addLevel(AffinityType type, int amount) {
        levels.put(type, Math.min(100, getLevel(type) + amount));
    }

    public void saveToNBT(CompoundTag nbt) {
        CompoundTag tag = new CompoundTag();
        levels.forEach((type, level) -> tag.putInt(type.name(), level));
        nbt.put("AffinityLevels", tag);
    }

    public void loadFromNBT(CompoundTag nbt) {
        if (nbt.contains("AffinityLevels")) {
            CompoundTag tag = nbt.getCompound("AffinityLevels");
            for (AffinityType type : AffinityType.values()) {
                if (tag.contains(type.name())) {
                    levels.put(type, tag.getInt(type.name()));
                }
            }
        }
    }
}