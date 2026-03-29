package com.otectus.arsnspells.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores per-school cast counts for the cross-mod progression system.
 * Cast counts are persistent and derive transient attribute bonuses.
 */
public class ProgressionData {
    public static final Capability<ProgressionData> PROGRESSION_DATA = CapabilityManager.get(new CapabilityToken<>() {});

    private final Map<String, Integer> schoolCastCounts = new HashMap<>();

    public int getCastCount(String school) {
        return schoolCastCounts.getOrDefault(school, 0);
    }

    public void incrementCastCount(String school) {
        schoolCastCounts.put(school, getCastCount(school) + 1);
    }

    /**
     * Calculate the transient attribute bonus for a school.
     * Growth: 0.1% per cast, capped at 25%.
     */
    public double getBonusForSchool(String school) {
        int casts = getCastCount(school);
        return Math.min(0.25, casts * 0.001);
    }

    public Map<String, Integer> getAllCastCounts() {
        return new HashMap<>(schoolCastCounts);
    }

    public void saveToNBT(CompoundTag nbt) {
        CompoundTag tag = new CompoundTag();
        schoolCastCounts.forEach(tag::putInt);
        nbt.put("ProgressionCounts", tag);
    }

    public void loadFromNBT(CompoundTag nbt) {
        schoolCastCounts.clear();
        if (nbt.contains("ProgressionCounts")) {
            CompoundTag tag = nbt.getCompound("ProgressionCounts");
            for (String key : tag.getAllKeys()) {
                schoolCastCounts.put(key, tag.getInt(key));
            }
        }
    }
}
