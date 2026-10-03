package com.otectus.arsnspells.config;

/**
 * The in-game settings row for {@code inscribed_ars_default_cooldown_ticks}: its label and the
 * preset values a click cycles through. Pure, so the cycling is unit-testable.
 */
public final class InscribedCooldownPresets {
    private InscribedCooldownPresets() {}

    static final int[] PRESETS = {0, 10, 20, 40, 60, 100, 200, 400};

    /** "Off", or ticks with their length in seconds at the normal 20 ticks per second. */
    public static String describe(int ticks) {
        if (ticks <= 0) return "Off";
        double seconds = ticks / 20.0;
        return ticks + "t (" + (seconds == Math.rint(seconds)
            ? String.valueOf((long) seconds) : String.valueOf(seconds)) + "s)";
    }

    /** The next preset above {@code current}, wrapping to Off after the largest. */
    public static int next(int current) {
        for (int preset : PRESETS) {
            if (preset > current) return preset;
        }
        return PRESETS[0];
    }
}
