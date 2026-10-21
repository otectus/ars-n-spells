package com.otectus.arsnspells.glyph;

import com.hollingsworth.arsnouveau.api.spell.AbstractCastMethod;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.SpellValidationError;
import com.hollingsworth.arsnouveau.common.spell.validation.BaseSpellValidationError;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ars spell validation for imported glyphs, appended to Ars's own standard validator so the
 * editor shows the errors as it shows its own, and casting refuses the same recipes.
 *
 * <p>Checks: the recipe's form is one the glyph's family supports (a Self-only glyph after
 * Touch is an error, not a silent self-buff); no more imported glyphs than
 * {@code max_imported_effects_per_spell}; and, at cast time, every imported glyph is available,
 * so a dormant glyph refuses the whole cast with its reason instead of being skipped silently.
 * Augment compatibility and limits are Ars's own checks, fed by each glyph's declared augments.
 */
public final class ImportedGlyphCompositionValidator {
    private ImportedGlyphCompositionValidator() {}

    public static List<SpellValidationError> validate(List<AbstractSpellPart> recipe, boolean castTime) {
        List<SpellValidationError> errors = new ArrayList<>();
        if (recipe == null || recipe.isEmpty()) return errors;
        AbstractSpellPart first = recipe.get(0);
        AbstractCastMethod form = first instanceof AbstractCastMethod method ? method : null;
        int limit;
        try {
            limit = Math.min(AnsConfig.MAX_IMPORTED_EFFECTS_PER_SPELL.get(), AnsConfig.MAX_IMPORTED_EFFECTS_CEILING);
        } catch (IllegalStateException unloaded) {
            limit = 4;
        }
        int count = 0;
        for (int position = 0; position < recipe.size(); position++) {
            if (!(recipe.get(position) instanceof ImportedIronEffect effect)) continue;
            count++;
            ImportedGlyphDefinition definition = effect.definition();
            if (form != null && !definition.family().supportsForm(form.getRegistryName())) {
                errors.add(new BaseSpellValidationError(position, effect, "ars_n_spells.imported_form",
                    effect.displayName(), Component.translatable(form.getLocalizationKey()),
                    Component.translatable("tooltip.ars_n_spells.glyph.restriction." + definition.family().name().toLowerCase(Locale.ROOT))));
            }
            if (count > limit) {
                errors.add(new BaseSpellValidationError(position, effect, "ars_n_spells.imported_limit",
                    Component.literal(Integer.toString(limit))));
            }
            if (castTime) {
                GlyphAvailability.Status status = GlyphAvailability.status(definition);
                if (!status.available())
                    errors.add(new BaseSpellValidationError(position, effect, "ars_n_spells.imported_dormant", status.message(definition)));
            }
        }
        return errors;
    }
}
