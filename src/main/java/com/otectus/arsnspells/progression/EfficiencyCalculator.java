package com.otectus.arsnspells.progression;

public class EfficiencyCalculator {
    public static float getManaCostReductionForSchoolLevel(int schoolLevel) {
        return schoolLevel * 0.01f; // 1% per level
    }

    public static float getDamageBoostForSchoolLevel(int schoolLevel) {
        return schoolLevel * 0.02f; // 2% per level
    }
}