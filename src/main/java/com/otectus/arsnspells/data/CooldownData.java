package com.otectus.arsnspells.data;

import com.mojang.serialization.Codec;
import com.otectus.arsnspells.cooldown.CooldownCategory;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Per-player cooldown end-tick timestamps keyed by category. Attachment
 * intentionally does NOT copy on death (cooldowns reset on respawn).
 */
public class CooldownData {
    public static final Codec<CooldownData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG)
        .xmap(
            raw -> {
                CooldownData d = new CooldownData();
                raw.forEach((k, v) -> {
                    try {
                        d.cooldowns.put(CooldownCategory.valueOf(k), v);
                    } catch (IllegalArgumentException ignored) {}
                });
                return d;
            },
            d -> d.cooldowns.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue))
        );

    private final Map<CooldownCategory, Long> cooldowns = new HashMap<>();

    public long getLastCast(CooldownCategory cat) {
        return cooldowns.getOrDefault(cat, 0L);
    }

    public void setLastCast(CooldownCategory cat, long time) {
        cooldowns.put(cat, time);
    }
}
