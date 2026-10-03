package com.otectus.arsnspells.affinity;

public class AffinityCalculator {
    public static float getDamageBonus(String schoolKey, int level) {
        return level * 0.005f; // 0.5% per level, shared with Forge
    }

    public static float getPenalty(String schoolKey, int level) {
        return level * 0.01f; // 1% per level
    }
}
