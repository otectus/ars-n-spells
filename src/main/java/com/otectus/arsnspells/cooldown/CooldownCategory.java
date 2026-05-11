package com.otectus.arsnspells.cooldown;

public enum CooldownCategory {
    OFFENSIVE("Offensive"), 
    DEFENSIVE("Defensive"), 
    UTILITY("Utility"), 
    MOVEMENT("Movement");

    private final String displayName;
    CooldownCategory(String name) { this.displayName = name; }
    public String getDisplayName() { return displayName; }
}