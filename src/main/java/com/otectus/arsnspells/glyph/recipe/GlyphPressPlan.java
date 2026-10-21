package com.otectus.arsnspells.glyph.recipe;

import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What a Glyph Press would do with its current inputs: either one exact conversion (which glyph,
 * at what terms) or the single reason it cannot. Immutable; the server derives it from the real
 * inputs every time it matters, and the screen only displays the server's answer.
 */
public record GlyphPressPlan(Reason reason, @Nullable ImportedGlyphDefinition definition, int tier,
                             @Nullable GlyphMappingSnapshot.PressTerms terms, ItemStack output) {

    /** Every outcome, in the order the planner checks them. The key is the localized message. */
    public enum Reason {
        OK("ok"),
        PRESS_DISABLED("press_disabled"),
        FEATURE_DISABLED("feature_disabled"),
        NO_SCROLL("no_scroll"),
        NOT_A_SCROLL("not_a_scroll"),
        ARS_EXPORT("ars_export"),
        PROXY_SPELL("proxy_spell"),
        BLANK_SCROLL("blank_scroll"),
        MULTIPLE_SPELLS("multiple_spells"),
        UNREADABLE("unreadable"),
        INVALID_LEVEL("invalid_level"),
        IRONS_ABSENT("irons_absent"),
        NO_ADAPTER("no_adapter"),
        LEVEL_NOT_CATALOGUED("level_not_catalogued"),
        SPELL_DISABLED("spell_disabled"),
        REQUIRES_LEARNING("requires_learning"),
        NO_PLATE("no_plate"),
        NEEDS_CATALYST("needs_catalyst"),
        CAPACITY_TOO_SMALL("capacity_too_small"),
        OUTPUT_FULL("output_full");

        private final String code;

        Reason(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public String messageKey() {
            return "ars_n_spells.glyph_press.reason." + code;
        }

        public static Reason fromOrdinal(int ordinal) {
            Reason[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NO_SCROLL;
        }
    }

    public static GlyphPressPlan refused(Reason reason) {
        return new GlyphPressPlan(reason, null, 0, null, ItemStack.EMPTY);
    }

    public static GlyphPressPlan refused(Reason reason, ImportedGlyphDefinition definition, int tier,
                                         GlyphMappingSnapshot.PressTerms terms) {
        return new GlyphPressPlan(reason, definition, tier, terms, ItemStack.EMPTY);
    }

    public boolean ok() {
        return reason == Reason.OK;
    }

    /** A refusal that still knows the spell (for the screen to show what would have been made). */
    public boolean identified() {
        return definition != null;
    }
}
