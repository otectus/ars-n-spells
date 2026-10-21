package com.otectus.arsnspells.glyph.recipe;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * What the Glyph Press read from its scroll slot, as plain data: the Iron's reader classifies the
 * stack once and the planner decides from this record alone.
 *
 * <p>Only a genuine Iron's scroll ({@code irons_spellbooks:scroll}) holding exactly one real spell
 * at a valid level reads as {@link Kind#SPELL}. Books, staffs, imbued gear, addon scroll items,
 * Spell Loom exports and ANS carriers are never read as a convertible scroll.
 */
public record ScrollReading(Kind kind, @Nullable ResourceLocation spellId, int level) {

    public enum Kind {
        /** Nothing in the slot. */
        EMPTY,
        /** Iron's is not installed, so no scroll can be read. */
        IRONS_ABSENT,
        /** Not a native Iron's scroll item. */
        NOT_A_SCROLL,
        /** A scroll with no spell, or only Iron's empty {@code none} spell. */
        BLANK,
        /** A Spell Loom export or ANS carrier: an Ars spell, not an Iron's spell. */
        EXPORTED_ARS_SPELL,
        /** More than one spell entry. */
        MULTIPLE_SPELLS,
        /** A level the spell does not have. */
        INVALID_LEVEL,
        /** Unreadable or unexpected container data. */
        MALFORMED,
        /** Exactly one Iron's spell at a valid level. */
        SPELL
    }

    public static final ScrollReading EMPTY = new ScrollReading(Kind.EMPTY, null, 0);
    public static final ScrollReading IRONS_ABSENT = new ScrollReading(Kind.IRONS_ABSENT, null, 0);
    public static final ScrollReading NOT_A_SCROLL = new ScrollReading(Kind.NOT_A_SCROLL, null, 0);
    public static final ScrollReading BLANK = new ScrollReading(Kind.BLANK, null, 0);
    public static final ScrollReading EXPORTED = new ScrollReading(Kind.EXPORTED_ARS_SPELL, null, 0);
    public static final ScrollReading MULTIPLE = new ScrollReading(Kind.MULTIPLE_SPELLS, null, 0);
    public static final ScrollReading MALFORMED = new ScrollReading(Kind.MALFORMED, null, 0);

    public static ScrollReading spell(ResourceLocation spellId, int level) {
        return new ScrollReading(Kind.SPELL, spellId, level);
    }

    public static ScrollReading invalidLevel(ResourceLocation spellId, int level) {
        return new ScrollReading(Kind.INVALID_LEVEL, spellId, level);
    }
}
