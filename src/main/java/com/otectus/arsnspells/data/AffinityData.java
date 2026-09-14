package com.otectus.arsnspells.data;

import com.otectus.arsnspells.affinity.AffinityType;
import com.otectus.arsnspells.util.SchoolKeys;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import java.util.HashMap;
import java.util.Map;

public class AffinityData {
    public static final Capability<AffinityData> AFFINITY_DATA = CapabilityManager.get(new CapabilityToken<>() {});

    /**
     * ANS-MED-013: server-main-thread only. All known mutation sites are event
     * handlers / packet handlers that dispatch via enqueueWork onto the main thread.
     * Plain HashMap is intentional; do NOT mutate from async tasks.
     */
    private final Map<String, Integer> levels = new HashMap<>();

    /** Fractional decay carried between decay intervals; see {@link DecayAccumulator}. */
    private final DecayAccumulator decayRemainders = new DecayAccumulator();

    public int getLevel(AffinityType type) {
        return getLevel(type.name());
    }

    public int getLevel(String school) { return levels.getOrDefault(SchoolKeys.normalize(school), 0); }
    public Map<String, Integer> getAllLevels() { return Map.copyOf(levels); }

    /** Full client snapshot: omitted schools are removals, including an empty disabled snapshot. */
    public void replaceLevels(Map<String, Integer> replacement) {
        levels.clear();
        replacement.forEach(this::setLevel);
    }

    public void setLevel(AffinityType type, int level) {
        setLevel(type.name(), level);
    }

    public void setLevel(String school, int level) {
        levels.put(SchoolKeys.normalize(school), Math.max(0, Math.min(100, level)));
    }
    public void addLevel(String school, int amount) {
        setLevel(school, (int) Math.max(0, Math.min(100L, (long) getLevel(school) + amount)));
    }

    public void addLevel(AffinityType type, int amount) {
        addLevel(type.name(), amount);
    }

    /**
     * Accumulates fractional decay for a school and returns the whole number of
     * points that should be removed now (0 on most intervals). See
     * {@link DecayAccumulator#accrue}.
     */
    public int accrueDecay(AffinityType type, double amount) {
        return decayRemainders.accrue(type, amount);
    }
    public int accrueDecay(String school, double amount) { return decayRemainders.accrue(school, amount); }

    /** Drops any carried fractional decay, e.g. once a school reaches level 0. */
    public void clearDecayRemainder(AffinityType type) {
        decayRemainders.clear(type);
    }
    public void clearDecayRemainder(String school) { decayRemainders.clear(school); }

    public void saveToNBT(CompoundTag nbt) {
        CompoundTag tag = new CompoundTag();
        levels.forEach(tag::putInt);
        nbt.put("AffinityLevels", tag);
        decayRemainders.saveToNBT(nbt, "AffinityDecayRemainders");
    }

    public void loadFromNBT(CompoundTag nbt) {
        // ANS-MED-012: clear before load so a second call cannot merge with stale state.
        levels.clear();
        if (nbt.contains("AffinityLevels")) {
            CompoundTag tag = nbt.getCompound("AffinityLevels");
            for (String key : tag.getAllKeys()) {
                levels.merge(SchoolKeys.normalize(key), Math.max(0, Math.min(100, tag.getInt(key))), Math::max);
            }
        }
        decayRemainders.loadFromNBT(nbt, "AffinityDecayRemainders");
    }
}
