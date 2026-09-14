package com.otectus.arsnspells.compat;

/** Blood Magic priority selects one complete source; it never combines partial LP and health. */
public final class LpSourcePolicy {
    public enum Source { BLOOD_MAGIC, HEALTH, UNAVAILABLE }
    private LpSourcePolicy() {}
    public static Source select(String mode, boolean bloodAvailable, double bloodBalance, double requested) {
        return switch (mode) {
            case "HEALTH_ONLY" -> Source.HEALTH;
            case "BLOOD_MAGIC_ONLY" -> bloodAvailable ? Source.BLOOD_MAGIC : Source.UNAVAILABLE;
            default -> bloodAvailable && bloodBalance >= requested ? Source.BLOOD_MAGIC : Source.HEALTH;
        };
    }
    public static double available(String mode, boolean bloodAvailable, double blood, double health) {
        return switch (mode) {
            case "HEALTH_ONLY" -> health;
            case "BLOOD_MAGIC_ONLY" -> bloodAvailable ? blood : 0;
            default -> Math.max(bloodAvailable ? blood : 0, health);
        };
    }
}
