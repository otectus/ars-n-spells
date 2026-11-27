package com.otectus.arsnspells.config;

import com.otectus.arsnspells.ArsNSpells;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Local presentation settings. This spec is safe to load before a world or server config exists. */
public final class AnsClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> IRON_GLYPH_DISPLAY_MODE;
    private static final Set<String> MODES = Set.of("grouped", "flat", "hidden");
    private static final AtomicReference<String> REPORTED_INVALID = new AtomicReference<>();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("spellbook");
        IRON_GLYPH_DISPLAY_MODE = builder
            .comment("How imported Iron's glyphs appear in supported Ars spellbook browsers.",
                "grouped: a separate library with one entry per spell and a level chooser.",
                "flat: individual available levels in the ordinary glyph palette.",
                "hidden: hide imported glyph browsing on this client.",
                "Does not erase knowledge, alter saved spells, or disable casting.")
            // Semantic validation is performed below so an invalid literal can be named in one
            // useful ANS warning before it is replaced with the safe default.
            .define("iron_glyph_display_mode", DisplayMode.GROUPED.id(), value -> value instanceof String);
        builder.pop();
        SPEC = builder.build();
    }

    private AnsClientConfig() {}

    public static DisplayMode mode() {
        try {
            String value = IRON_GLYPH_DISPLAY_MODE.get();
            if (MODES.contains(value)) return DisplayMode.parse(value);
            warnInvalid(value);
        } catch (IllegalStateException notLoadedYet) {
            // The title screen can construct before config loading completes. The documented default is safe.
        }
        return DisplayMode.GROUPED;
    }

    public static void setMode(DisplayMode mode) {
        if (mode == null) mode = DisplayMode.GROUPED;
        try {
            IRON_GLYPH_DISPLAY_MODE.set(mode.id());
            SPEC.save();
        } catch (IllegalStateException notLoadedYet) {
            ArsNSpells.LOGGER.warn("[GlyphBrowser] client config is not loaded; the display mode was not changed");
        }
    }

    /** Called only for this exact CLIENT spec after NeoForge has accepted/corrected it. */
    public static void onConfigAccepted() {
        try {
            String value = IRON_GLYPH_DISPLAY_MODE.getRaw();
            if (!MODES.contains(value)) {
                warnInvalid(value);
                IRON_GLYPH_DISPLAY_MODE.set(DisplayMode.GROUPED.id());
                SPEC.save();
            }
        } catch (IllegalStateException ignored) {
            // A defensive guard for unusual loader ordering; mode() still returns grouped.
        }
    }

    private static void warnInvalid(String value) {
        String shown = String.valueOf(value);
        if (!shown.equals(REPORTED_INVALID.getAndSet(shown))) {
            ArsNSpells.LOGGER.warn("[GlyphBrowser] invalid iron_glyph_display_mode '{}'; using grouped (valid: grouped, flat, hidden)", shown);
        }
    }
}
