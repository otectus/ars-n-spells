package com.otectus.arsnspells.progression;

import java.util.HashMap;
import java.util.Map;

public class ProgressionData {
    private final Map<SpellSchool, Integer> schoolXP = new HashMap<>();
    private final Map<SpellSchool, Integer> schoolLevels = new HashMap<>();

    public boolean addXP(SpellSchool school, int xp) { return true; }
    public int getXP(SpellSchool school) { return 0; }
    public int getLevel(SpellSchool school) { return 0; }
    public int getXPForNextLevel(SpellSchool school) { return 1000; }
    public double getProgressToNextLevel(SpellSchool school) { return 0.0; }
}