package com.otectus.arsnspells.glyph.recipe;

import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.behavior.Dormancy;
import com.otectus.arsnspells.glyph.behavior.GlyphBehaviorSnapshot;
import com.otectus.arsnspells.glyph.behavior.VariantBehavior;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * The Glyph Press's one conversion rule, from plain inputs to a named outcome. The menu preview,
 * the Inscribe action, the job's completion check and the recipe viewer all ask this, so what a
 * player is shown is what the server will do.
 *
 * <p>The order of checks is the order a player fixes problems in: the scroll first, then what the
 * server allows, then the player's own prerequisites, then materials, then room for the result.
 */
public final class GlyphPressPlan {

    /** Why the Press can or cannot inscribe; each has a localized message. Ordinals are synced, so only append. */
    public enum Reason {
        OK,
        INSERT_SCROLL,
        IRONS_ABSENT,
        NOT_A_SCROLL,
        BLANK_SCROLL,
        EXPORTED_ARS_SPELL,
        MULTIPLE_SPELLS,
        MALFORMED_SCROLL,
        INVALID_LEVEL,
        NO_ADAPTER,
        UNSUPPORTED_SPELL,
        LEVEL_NOT_CATALOGUED,
        PRESS_DISABLED,
        GLYPHS_DISABLED,
        SPELL_DISABLED,
        DISABLED_BY_PACK,
        LEVEL_ABOVE_SERVER_MAX,
        NOT_SYNCED,
        NOT_LEARNED,
        INSERT_PLATE,
        NEEDS_CATALYST,
        SOURCE_OVER_CAPACITY,
        OUTPUT_FULL,
        /** A job is already running. */
        BUSY;

        public String translationKey() {
            return "ars_n_spells.glyph_press.reason." + name().toLowerCase(Locale.ROOT);
        }

        public static Reason byOrdinal(int ordinal) {
            Reason[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : INSERT_SCROLL;
        }
    }

    /**
     * Everything the rule reads.
     *
     * @param learned        whether the acting player has learned the spell in Iron's; null when
     *                       no player acts (a check made without one skips this prerequisite)
     * @param catalystInTag  whether the catalyst slot's item is in a given item tag
     * @param outputItem     the output slot's item id, or null when empty
     */
    public record Inputs(ScrollReading scroll, boolean plate, int catalystCount, Predicate<String> catalystInTag,
                         @Nullable ResourceLocation outputItem, int outputCount, int outputMax,
                         @Nullable Boolean learned, int sourceCapacity) {
    }

    /** The outcome, with the conversion's terms whenever the scroll named a catalogued spell level. */
    public record Plan(Reason reason, @Nullable ImportedGlyphKey key, @Nullable CatalogEntry entry,
                       @Nullable VariantBehavior behavior) {
        public boolean ok() {
            return reason == Reason.OK;
        }

        /** Same conversion terms as {@code other}: same glyph, tier, catalyst, Source and time. */
        public boolean sameTerms(Plan other) {
            return other != null && key != null && key.equals(other.key) && behavior != null && other.behavior != null
                && behavior.tier() == other.behavior.tier() && behavior.pressGems() == other.behavior.pressGems()
                && behavior.pressSource() == other.behavior.pressSource() && behavior.pressTicks() == other.behavior.pressTicks()
                && behavior.catalyst().equals(other.behavior.catalyst());
        }
    }

    private GlyphPressPlan() {
    }

    public static Plan plan(Inputs in, ImportedGlyphCatalog catalog, GlyphBehaviorSnapshot snapshot) {
        if (!snapshot.settings().pressEnabled()) return refuse(Reason.PRESS_DISABLED);
        ScrollReading scroll = in.scroll();
        switch (scroll.kind()) {
            case EMPTY -> { return refuse(Reason.INSERT_SCROLL); }
            case IRONS_ABSENT -> { return refuse(Reason.IRONS_ABSENT); }
            case NOT_A_SCROLL -> { return refuse(Reason.NOT_A_SCROLL); }
            case BLANK -> { return refuse(Reason.BLANK_SCROLL); }
            case EXPORTED_ARS_SPELL -> { return refuse(Reason.EXPORTED_ARS_SPELL); }
            case MULTIPLE_SPELLS -> { return refuse(Reason.MULTIPLE_SPELLS); }
            case MALFORMED -> { return refuse(Reason.MALFORMED_SCROLL); }
            case INVALID_LEVEL -> { return refuse(Reason.INVALID_LEVEL); }
            case SPELL -> { }
        }
        CatalogEntry entry = catalog.entry(scroll.spellId());
        if (entry == null) return refuse(Reason.NO_ADAPTER);
        if (!entry.supported()) return new Plan(Reason.UNSUPPORTED_SPELL, null, entry, null);
        if (scroll.level() < entry.minLevel() || scroll.level() > entry.maxLevel()) {
            return new Plan(Reason.LEVEL_NOT_CATALOGUED, null, entry, null);
        }
        ImportedGlyphKey key = new ImportedGlyphKey(entry.spellId(), scroll.level());
        VariantBehavior behavior = snapshot.variant(key);
        Reason dormant = dormantReason(behavior.dormancy());
        if (dormant != null) return new Plan(dormant, key, entry, behavior);
        if (entry.requiresLearning() && Boolean.FALSE.equals(in.learned())) return new Plan(Reason.NOT_LEARNED, key, entry, behavior);
        if (!in.plate()) return new Plan(Reason.INSERT_PLATE, key, entry, behavior);
        if (in.catalystCount() < behavior.pressGems() || !in.catalystInTag().test(behavior.catalyst())) {
            return new Plan(Reason.NEEDS_CATALYST, key, entry, behavior);
        }
        if (behavior.pressSource() > in.sourceCapacity()) return new Plan(Reason.SOURCE_OVER_CAPACITY, key, entry, behavior);
        if (in.outputItem() != null && (!in.outputItem().equals(key.itemId()) || in.outputCount() >= in.outputMax())) {
            return new Plan(Reason.OUTPUT_FULL, key, entry, behavior);
        }
        return new Plan(Reason.OK, key, entry, behavior);
    }

    @Nullable
    static Reason dormantReason(Dormancy dormancy) {
        return switch (dormancy) {
            case AVAILABLE -> null;
            case FEATURE_DISABLED -> Reason.GLYPHS_DISABLED;
            case IRONS_ABSENT -> Reason.IRONS_ABSENT;
            case SPELL_NOT_REGISTERED, SPELL_DISABLED_BY_IRONS -> Reason.SPELL_DISABLED;
            case DISABLED_BY_PACK -> Reason.DISABLED_BY_PACK;
            case LEVEL_ABOVE_SERVER_MAX -> Reason.LEVEL_ABOVE_SERVER_MAX;
            case UNSUPPORTED -> Reason.UNSUPPORTED_SPELL;
            case NOT_SYNCED -> Reason.NOT_SYNCED;
        };
    }

    private static Plan refuse(Reason reason) {
        return new Plan(reason, null, null, null);
    }
}
