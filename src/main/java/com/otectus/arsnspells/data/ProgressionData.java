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
    /**
     * Cast counts are a monotonic tally, so anything off disk that is not a non-negative count
     * under a non-empty key is garbage rather than data. Sanitizing on the way in matters
     * because {@link #getBonusForSchool} multiplies the count by a per-cast rate: a negative
     * count becomes a negative attribute bonus, and there is no floor downstream to catch it.
     * The previous {@code putAll} copied whatever was there straight through.
     */
    public static final Codec<ProgressionData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
        .xmap(
            raw -> {
                ProgressionData d = new ProgressionData();
                raw.forEach((school, count) -> {
                    if (school == null || school.isEmpty() || count == null || count <= 0) {
                        return;
                    }
                    d.schoolCastCounts.merge(com.otectus.arsnspells.util.SchoolKeys.normalize(school), count, Math::max);
                });
                return d;
            },
            d -> new HashMap<>(d.schoolCastCounts)
        );

    private final Map<String, Integer> schoolCastCounts = new HashMap<>();

    public int getCastCount(String school) {
        return schoolCastCounts.getOrDefault(com.otectus.arsnspells.util.SchoolKeys.normalize(school), 0);
    }

    public void incrementCastCount(String school) {
        schoolCastCounts.put(com.otectus.arsnspells.util.SchoolKeys.normalize(school), (int) Math.min(Integer.MAX_VALUE, (long) getCastCount(school) + 1));
    }

    /**
     * Calculate the transient attribute bonus for a school.
     * Growth: 0.1% per cast, capped at 25%.
     */
    public double getBonusForSchool(String school) {
        int casts = getCastCount(school);
        // Audit F4: config-driven, was hardcoded 0.001 / 0.25. The bonus is transient
        // (recomputed from the persistent cast count), so a config change rescales every
        // player's bonus immediately rather than needing a data migration.
        double perCast = 0.001;
        double cap = 0.25;
        try {
            perCast = com.otectus.arsnspells.config.AnsConfig.PROGRESSION_BONUS_PER_CAST.get();
            cap = com.otectus.arsnspells.config.AnsConfig.PROGRESSION_BONUS_CAP.get();
        } catch (IllegalStateException configNotReady) {
            // Config not loaded yet (very early tick) - use the historical defaults.
        }
        return Math.min(cap, casts * perCast);
    }

    public Map<String, Integer> getAllCastCounts() {
        return new HashMap<>(schoolCastCounts);
    }
}
