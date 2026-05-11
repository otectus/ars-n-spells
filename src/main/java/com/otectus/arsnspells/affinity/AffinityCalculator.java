package com.otectus.arsnspells.affinity;

/**
 * Pure helpers for translating an integer affinity level into damage and
 * penalty multipliers. Used by {@link AffinityBonuses} so the curve lives
 * in one place — earlier versions left this class with no callers.
 */
public class AffinityCalculator {
    /** Damage bonus contribution per affinity level (additive, fractional). */
    public static float getDamageBonus(AffinityType type, int level) {
        return level * 0.005f; // 0.5% per level — matches AffinityBonuses curve
    }

    /**
     * Penalty contribution per affinity level (currently unused — reserved
     * for an opposing-school penalty design that 1.9.0 does not enable.
     * Kept as a stable hook so a future update can wire it without API drift).
     */
    public static float getPenalty(AffinityType type, int level) {
        return level * 0.01f;
    }
}
