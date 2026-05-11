package com.otectus.arsnspells.data;

import com.mojang.serialization.Codec;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-player school cast counts driving the cross-mod progression bonuses.
 * Persists via {@link #CODEC}; copies on death (long-term progression
 * survives respawn).
 */
public class ProgressionData {
    public static final Codec<ProgressionData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
        .xmap(
            raw -> {
                ProgressionData d = new ProgressionData();
                d.schoolCastCounts.putAll(raw);
                return d;
            },
            d -> new HashMap<>(d.schoolCastCounts)
        );

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
}
