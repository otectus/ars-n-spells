package com.otectus.arsnspells.data;

import com.otectus.arsnspells.affinity.AffinityType;
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

    private final Map<AffinityType, Double> remainders = new HashMap<>();

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
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return 0;
        }
        double total = remainders.getOrDefault(type, 0.0) + amount;
        int whole = (int) Math.floor(total);
        remainders.put(type, total - whole);
        return whole;
    }

    /** Drops any carried fractional decay, e.g. once a school reaches level 0. */
    public void clear(AffinityType type) {
        remainders.remove(type);
    }

    public void saveToNBT(CompoundTag nbt, String key) {
        CompoundTag tag = new CompoundTag();
        remainders.forEach((type, remainder) -> tag.putDouble(type.name(), remainder));
        nbt.put(key, tag);
    }

    public void loadFromNBT(CompoundTag nbt, String key) {
        remainders.clear();
        if (!nbt.contains(key)) {
            return;
        }
        CompoundTag tag = nbt.getCompound(key);
        for (AffinityType type : AffinityType.values()) {
            if (tag.contains(type.name())) {
                double remainder = tag.getDouble(type.name());
                // Sanitize hand-edited/corrupt NBT: a remainder is by construction in [0, 1).
                if (Double.isFinite(remainder) && remainder > 0.0) {
                    remainders.put(type, Math.min(remainder, Math.nextDown(1.0)));
                }
            }
        }
    }
}
