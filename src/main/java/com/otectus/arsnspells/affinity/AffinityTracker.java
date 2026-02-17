package com.otectus.arsnspells.affinity;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.player.Player;

public class AffinityTracker {
    private final Map<AffinityType, Integer> affinityLevels = new HashMap<>();

    public void increaseAffinity(AffinityType type, int amount) {
        int current = affinityLevels.getOrDefault(type, 0);
        affinityLevels.put(type, Math.min(100, current + amount));
    }

    public float getAffinityMultiplier(AffinityType type) {
        // Returns 1.0 + 1% per level bonus
        return 1.0f + (affinityLevels.getOrDefault(type, 0) * 0.01f);
    }
}