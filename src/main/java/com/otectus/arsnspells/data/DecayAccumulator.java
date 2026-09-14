package com.otectus.arsnspells.data;

import com.otectus.arsnspells.affinity.AffinityType;
import com.otectus.arsnspells.util.SchoolKeys;
import net.minecraft.nbt.CompoundTag;
import java.util.HashMap;
import java.util.Map;

/**
 * Fractional decay carried between affinity decay intervals, always in [0, 1)
 * per school. With default decay settings a level loses well under one point
 * per interval; without this residual the handler's integer floor forced a
 * flat 1 point/interval (~20x the documented proportional rate — audit D1).
 *
 * <p>Lives outside {@link AffinityData} so the math is unit-testable:
 * AffinityData's static {@code CapabilityToken} initializer requires a Forge
 * runtime transformer and cannot load in plain JUnit.
 *
 * <p>Same threading contract as AffinityData (ANS-MED-013): server main
 * thread only; plain HashMap is intentional.
 */
public final class DecayAccumulator {

    private final Map<String, Double> remainders = new HashMap<>();

    /**
     * Accumulates fractional decay for a school and returns the whole number of
     * points that should be removed now (0 on most intervals). The sub-1.0
     * remainder is retained (and persisted) so slow proportional decay is
     * honored across intervals, saves, and relogs instead of rounding up.
     *
     * @param amount fractional decay accrued this interval; non-finite or
     *               negative values are ignored and leave the remainder unchanged
     */
    public int accrue(AffinityType type, double amount) {
        return accrue(type.name(), amount);
    }

    public int accrue(String school, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return 0;
        }
        String key = SchoolKeys.normalize(school);
        double total = remainders.getOrDefault(key, 0.0) + amount;
        int whole = (int) Math.floor(total);
        remainders.put(key, total - whole);
        return whole;
    }

    /** Drops any carried fractional decay, e.g. once a school reaches level 0. */
    public void clear(AffinityType type) {
        clear(type.name());
    }
    public void clear(String school) { remainders.remove(SchoolKeys.normalize(school)); }

    public void saveToNBT(CompoundTag nbt, String key) {
        CompoundTag tag = new CompoundTag();
        remainders.forEach(tag::putDouble);
        nbt.put(key, tag);
    }

    public void loadFromNBT(CompoundTag nbt, String key) {
        remainders.clear();
        if (!nbt.contains(key)) {
            return;
        }
        CompoundTag tag = nbt.getCompound(key);
        for (String type : tag.getAllKeys()) {
            if (tag.contains(type)) {
                double remainder = tag.getDouble(type);
                // Sanitize hand-edited/corrupt NBT: a remainder is by construction in [0, 1).
                if (Double.isFinite(remainder) && remainder > 0.0) {
                    remainders.merge(SchoolKeys.normalize(type), Math.min(remainder, Math.nextDown(1.0)), Math::max);
                }
            }
        }
    }
}
