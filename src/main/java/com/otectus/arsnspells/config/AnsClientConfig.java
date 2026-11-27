package com.otectus.arsnspells.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import java.util.List;

/** Local display only. Forge corrects invalid TOML values to the default and logs the correction. */
public final class AnsClientConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.ConfigValue<String> MODE;
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("spellbook");
        MODE = builder.comment("How converted Iron's spells appear in your Ars spellbook.",
            "grouped: a library with one entry per spell; flat: individual available levels; hidden: hide imported browsing.",
            "Your learned glyphs, saved spells, and casting are preserved.")
            .defineInList("iron_glyph_display_mode", "grouped", List.of("grouped", "flat", "hidden"));
        builder.pop();
        SPEC = builder.build();
    }
    private AnsClientConfig() {}

    public static DisplayMode mode() { return SPEC.isLoaded() ? DisplayMode.parse(MODE.get()) : DisplayMode.GROUPED; }
    public static void setMode(DisplayMode mode) {
        MODE.set(mode.id());
        MODE.save();
    }

    public static boolean isServerSpec(ModConfig config) {
        return config.getType() == ModConfig.Type.SERVER && config.getSpec() == AnsConfig.SPEC;
    }
}
