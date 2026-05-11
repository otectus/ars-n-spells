package com.otectus.arsnspells.data;

import com.mojang.serialization.Codec;
import com.otectus.arsnspells.affinity.AffinityType;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Per-player affinity levels keyed by school. Replaces the Forge capability
 * pattern with a plain data class; the Codec on {@link #CODEC} drives
 * NeoForge attachment persistence.
 */
public class AffinityData {
    /**
     * Disk shape: a flat map of {@code AffinityType.name() -> level}. Keys
     * that don't map to a known {@link AffinityType} (e.g. removed schools)
     * are silently skipped on load to keep saves forward-compatible.
     */
    public static final Codec<AffinityData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
        .xmap(
            raw -> {
                AffinityData d = new AffinityData();
                raw.forEach((k, v) -> {
                    try {
                        d.levels.put(AffinityType.valueOf(k), v);
                    } catch (IllegalArgumentException ignored) {}
                });
                return d;
            },
            d -> d.levels.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue))
        );

    private final Map<AffinityType, Integer> levels = new HashMap<>();

    public int getLevel(AffinityType type) {
        return levels.getOrDefault(type, 0);
    }

    public void setLevel(AffinityType type, int level) {
        levels.put(type, Math.max(0, Math.min(100, level)));
    }

    public void addLevel(AffinityType type, int amount) {
        setLevel(type, getLevel(type) + amount);
    }
}
