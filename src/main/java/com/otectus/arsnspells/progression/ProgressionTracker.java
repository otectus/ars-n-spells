package com.otectus.arsnspells.progression;

import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

public class ProgressionTracker {
    private final Map<String, Integer> spellCounts = new HashMap<>(); // e.g., "fire" -> count
    private final Map<String, Integer> schoolLevels = new HashMap<>(); // e.g., "FIRE" -> level

    public void incrementSpellCast(String elementType) {
        spellCounts.put(elementType, spellCounts.getOrDefault(elementType, 0) + 1);
    }

    public int getSpellCount(String elementType) {
        return spellCounts.getOrDefault(elementType, 0);
    }

    public void setSchoolLevel(String school, int level) {
        schoolLevels.put(school, level);
    }

    public int getSchoolLevel(String school) {
        return schoolLevels.getOrDefault(school, 0);
    }
}