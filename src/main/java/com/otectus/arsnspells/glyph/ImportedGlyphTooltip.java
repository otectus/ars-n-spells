package com.otectus.arsnspells.glyph;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** Side-neutral tooltip lines shared by the glyph item, the Press screen and JEI. */
public final class ImportedGlyphTooltip {
    private ImportedGlyphTooltip() {}

    public static void append(ImportedGlyphDefinition definition, List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.ars_n_spells.glyph.base_level", definition.levelNumeral(),
                com.otectus.arsnspells.glyph.behavior.GlyphBehavior.effectiveMax(definition))
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.ars_n_spells.glyph.school", schoolName(definition))
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.ars_nouveau.glyph_level", GlyphAvailability.tier(definition))
            .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.ars_n_spells.glyph.restriction." + definition.family().name().toLowerCase(Locale.ROOT))
            .withStyle(ChatFormatting.DARK_AQUA));
        if (!definition.instant())
            tooltip.add(Component.translatable("tooltip.ars_n_spells.glyph.cast_type." + definition.castType().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.DARK_AQUA));
        if (definition.level() < com.otectus.arsnspells.glyph.behavior.GlyphBehavior.effectiveMax(definition) || definition.canDampen())
            tooltip.add(Component.translatable("tooltip.ars_n_spells.glyph.augments").withStyle(ChatFormatting.DARK_GRAY));
        if (com.otectus.arsnspells.glyph.behavior.GlyphBehavior.requiresLearning(definition))
            tooltip.add(Component.translatable("tooltip.ars_n_spells.glyph.requires_learning").withStyle(ChatFormatting.LIGHT_PURPLE));
        GlyphAvailability.Status status = GlyphAvailability.status(definition);
        if (!status.available()) tooltip.add(status.message(definition).copy().withStyle(ChatFormatting.RED));
    }

    /** The spell's effective school (it can be reconfigured in Iron's), by its translated name. */
    public static Component schoolName(ImportedGlyphDefinition definition) {
        net.minecraft.resources.ResourceLocation school = com.otectus.arsnspells.glyph.behavior.GlyphBehavior.school(definition);
        String key = "school." + school.getNamespace() + "." + school.getPath();
        return net.minecraft.locale.Language.getInstance().has(key) ? Component.translatable(key)
            : Component.literal(school.getPath());
    }
}
