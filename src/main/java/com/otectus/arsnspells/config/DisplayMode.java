package com.otectus.arsnspells.config;

/** Stable, case-sensitive values in the player's CLIENT config. */
public enum DisplayMode {
    GROUPED("grouped"), FLAT("flat"), HIDDEN("hidden");

    private final String id;
    DisplayMode(String id) { this.id = id; }
    public String id() { return id; }

    public static DisplayMode parse(String value) {
        for (DisplayMode mode : values()) if (mode.id.equals(value)) return mode;
        return GROUPED;
    }
}
