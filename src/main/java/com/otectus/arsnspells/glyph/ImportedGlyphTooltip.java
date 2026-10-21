package com.otectus.arsnspells.glyph;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** Side-neutral tooltip lines shared by the glyph item, the Press screen and JEI. */
public final class ImportedGlyphTooltip {
    private ImportedGlyphTooltip() {}

    public static void append(ImportedGlyphDefinition definition, List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.ars_n_spells.glyph.base_level", definition.levelNumeral(), definition.maxLevel())
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
        if (definition.canAmplify() || definition.canDampen())
            tooltip.add(Component.translatable("tooltip.ars_n_spells.glyph.augments").withStyle(ChatFormatting.DARK_GRAY));
        if (definition.requiresLearning())
            tooltip.add(Component.translatable("tooltip.ars_n_spells.glyph.requires_learning").withStyle(ChatFormatting.LIGHT_PURPLE));
        GlyphAvailability.Status status = GlyphAvailability.status(definition);
        if (!status.available()) tooltip.add(status.message(definition).copy().withStyle(ChatFormatting.RED));
    }

    public static Component schoolName(ImportedGlyphDefinition definition) {
        String key = "school." + definition.school().getNamespace() + "." + definition.school().getPath();
        return net.minecraft.locale.Language.getInstance().has(key) ? Component.translatable(key)
            : Component.literal(definition.school().getPath());
    }
}
