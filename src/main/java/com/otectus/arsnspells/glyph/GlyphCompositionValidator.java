package com.otectus.arsnspells.glyph;

import com.hollingsworth.arsnouveau.api.spell.AbstractCastMethod;
import com.hollingsworth.arsnouveau.api.spell.AbstractEffect;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.ISpellValidator;
import com.hollingsworth.arsnouveau.api.spell.SpellValidationError;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.GlyphBehaviorSnapshot;
import com.otectus.arsnspells.glyph.behavior.VariantBehavior;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ars spell validation for imported glyphs, added to both of Ars's validators (the spellbook
 * editor's and the one every cast runs) so an unsupported composition is shown while the spell is
 * written and refused before any cast event, payment or cooldown.
 *
 * <p>Rules, all from the startup catalog and the current behaviour snapshot:
 * <ul>
 *   <li>a dormant glyph (feature off, Iron's absent, spell disabled, level above the server's
 *       maximum, pack-disabled) cannot be cast and says why;</li>
 *   <li>the spell's form must fit the glyph's target: a caster-only glyph needs Self, a glyph
 *       that needs a living target cannot use Self or Underfoot, a block glyph cannot use Self;</li>
 *   <li>at most {@code max_imported_effects_per_spell} imported effects per spell;</li>
 *   <li>a glyph that moves the caster to another dimension must be the last effect.</li>
 * </ul>
 * Augment compatibility and limits are Ars's own validators, fed by each glyph's declared
 * augments ({@link ImportedIronEffect}).
 */
public final class GlyphCompositionValidator implements ISpellValidator {

    public static final GlyphCompositionValidator INSTANCE = new GlyphCompositionValidator();

    /** Form ids Ars Nouveau 5.13 ships; any other form is an addon form that resolves at a hit. */
    static final String SELF = "ars_nouveau:glyph_self";
    static final String UNDERFOOT = "ars_nouveau:glyph_underfoot";

    private GlyphCompositionValidator() {
    }

    @Override
    public List<SpellValidationError> validate(List<AbstractSpellPart> recipe) {
        List<SpellValidationError> errors = new ArrayList<>();
        if (recipe == null || recipe.isEmpty()) return errors;
        GlyphBehaviorSnapshot snapshot = null;
        String form = recipe.get(0) instanceof AbstractCastMethod method ? method.getRegistryName().toString() : null;
        int imported = 0;
        int lastEffect = -1;
        for (int i = 0; i < recipe.size(); i++) {
            if (recipe.get(i) instanceof AbstractEffect) lastEffect = i;
        }
        for (int i = 0; i < recipe.size(); i++) {
            if (!(recipe.get(i) instanceof ImportedIronEffect effect)) continue;
            if (snapshot == null) snapshot = GlyphBehavior.current();
            imported++;
            VariantBehavior behavior = snapshot.variant(effect.key());
            if (!behavior.available()) {
                errors.add(new Error(i, effect, "dormant", effect.spellName(),
                    Component.translatable(behavior.dormancy().translationKey())));
                continue;
            }
            String refusal = formRefusal(effect.entry(), form, snapshot.selfOnly(effect.entry().spellId()));
            if (refusal != null) {
                errors.add(new Error(i, effect, refusal, effect.spellName()));
            }
            if (effect.entry().finalEffectOnly() && i != lastEffect) {
                errors.add(new Error(i, effect, "must_be_last", effect.spellName()));
            }
            if (imported > snapshot.settings().maxImportedEffects()) {
                errors.add(new Error(i, effect, "too_many", Component.literal(Integer.toString(snapshot.settings().maxImportedEffects()))));
            }
        }
        return errors;
    }

    /** Null when {@code form} can deliver to the glyph's target, else the validation code. */
    @Nullable
    static String formRefusal(CatalogEntry entry, @Nullable String form, boolean selfOnlyByPack) {
        if (form == null) return null;
        boolean self = SELF.equals(form);
        boolean underfoot = UNDERFOOT.equals(form);
        if (selfOnlyByPack && !self) return "self_only";
        return switch (entry.target()) {
            case SELF -> self ? null : "self_only";
            case ENTITY -> self || underfoot ? "living_target" : null;
            case ENTITY_OR_SELF -> underfoot ? "living_target" : null;
            case BLOCK -> self ? "block_target" : null;
            case POSITION, ANY -> null;
        };
    }

    /** An Ars validation error with ANS wording. */
    static final class Error implements SpellValidationError {
        private final int position;
        private final AbstractSpellPart part;
        private final String code;
        private final Object[] args;

        Error(int position, AbstractSpellPart part, String code, Object... args) {
            this.position = position;
            this.part = part;
            this.code = code;
            this.args = args;
        }

        public String code() {
            return code;
        }

        @Override
        public int getPosition() {
            return position;
        }

        @Nullable
        @Override
        public AbstractSpellPart getSpellPart() {
            return part;
        }

        @Override
        public MutableComponent makeTextComponentExisting() {
            return Component.translatable("ars_n_spells.glyph.validation." + code, args);
        }

        @Override
        public MutableComponent makeTextComponentAdding() {
            return Component.translatable("ars_n_spells.glyph.validation." + code, args);
        }
    }
}
