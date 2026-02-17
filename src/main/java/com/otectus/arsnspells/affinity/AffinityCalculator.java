package com.otectus.arsnspells.affinity;

public class AffinityCalculator {
    public static float getDamageBonus(AffinityType type, int level) {
        return level * 0.02f; // 2% per level
    }

    public static float getPenalty(AffinityType type, int level) {
        return level * 0.01f; // 1% per level
    }
}