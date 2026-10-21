package com.otectus.arsnspells.glyph.behavior;

/**
 * The effective behaviour of one imported glyph variant under the current snapshot.
 *
 * @param tier        Ars book tier (1..3) the glyph requires
 * @param pressGems   Source gems (or other catalyst items) one conversion consumes
 * @param pressSource Source one conversion consumes from the Press's buffer
 * @param pressTicks  conversion time in ticks
 * @param catalyst    item tag id of the catalyst ingredient
 */
public record VariantBehavior(Dormancy dormancy, int tier, int pressGems, int pressSource, int pressTicks,
                              String catalyst) {

    public static final String DEFAULT_CATALYST = "ars_n_spells:glyph_press_catalysts";

    public boolean available() {
        return dormancy.available();
    }

    /** The default Press tuning for a conversion tier (spec 3.4.0 §7.2). */
    public static VariantBehavior defaults(Dormancy dormancy, int tier) {
        return switch (Math.max(1, Math.min(3, tier))) {
            case 1 -> new VariantBehavior(dormancy, 1, 1, 500, 100, DEFAULT_CATALYST);
            case 2 -> new VariantBehavior(dormancy, 2, 2, 1500, 140, DEFAULT_CATALYST);
            default -> new VariantBehavior(dormancy, 3, 4, 3000, 200, DEFAULT_CATALYST);
        };
    }
}
