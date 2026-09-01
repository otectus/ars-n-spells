package com.otectus.arsnspells.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the "addon removed while an exported item still exists" case.
 *
 * <p>Two halves are exercised. The message formatting is pure and easy to get wrong. The
 * serialized-shape parsing matters because Ars 5.x changed it: {@code Spell.CODEC}'s
 * {@code recipe} field is now a list of glyph id strings, where 1.20.1 wrote a {@code recipe}
 * compound with {@code size} plus {@code part0..partN}. Reading the old shape here would find
 * nothing and report every payload intact - the exact silent failure this class exists to
 * prevent.
 *
 * <p>Ars Nouveau's glyph registry turns out to be populated under ModDevGradle's unitTest
 * harness (Ars is a plain {@code implementation} dependency and its registration runs), so the
 * registry-backed half is directly testable here rather than only in a GameTest: a stock Ars
 * glyph resolves and an invented id does not.
 */
class ArsSpellIntegrityTest {

    @Test
    void describeMissing_isEmptyForAnIntactPayload() {
        assertEquals("", ArsSpellIntegrity.describeMissing(List.of()));
        assertEquals("", ArsSpellIntegrity.describeMissing(null));
    }

    @Test
    void describeMissing_listsEachMissingGlyph() {
        String text = ArsSpellIntegrity.describeMissing(
            List.of("ars_elemental:glyph_water_grave", "ars_elemental:glyph_spark"));
        assertTrue(text.contains("ars_elemental:glyph_water_grave"), text);
        assertTrue(text.contains("ars_elemental:glyph_spark"), text);
    }

    @Test
    void describeMissing_truncatesLongListsRatherThanFloodingChat() {
        String text = ArsSpellIntegrity.describeMissing(
            List.of("a:one", "b:two", "c:three", "d:four", "e:five"));
        assertTrue(text.contains("a:one"), text);
        assertTrue(text.contains("c:three"), text);
        assertTrue(text.contains("(+2 more)"),
            "a badly broken payload must summarise rather than print an unreadable wall: " + text);
        assertTrue(!text.contains("d:four"), "entries past the cap must be summarised, not shown");
    }

    private static CompoundTag payloadWithRecipe(String... glyphIds) {
        CompoundTag spell = new CompoundTag();
        ListTag recipe = new ListTag();
        for (String id : glyphIds) {
            recipe.add(StringTag.valueOf(id));
        }
        spell.put("recipe", recipe);
        return spell;
    }

    @Test
    void missingGlyphIds_readsTheArs5RecipeListShape() {
        // A stock Ars glyph plus one from an addon that is not installed. Only the absent one
        // may be reported - which simultaneously proves the parser read Spell.CODEC's `recipe`
        // list of id strings at all. Reading 1.20.1's size/part0..N compound would find nothing
        // and declare this broken payload intact, which is the silent failure being guarded.
        List<String> missing = ArsSpellIntegrity.missingGlyphIds(
            payloadWithRecipe("ars_nouveau:glyph_projectile", "ars_elemental:glyph_water_grave"));
        assertEquals(List.of("ars_elemental:glyph_water_grave"), missing,
            "only the uninstalled addon glyph may be reported missing");
    }

    @Test
    void isIntact_isTrueForAnAllStockRecipe() {
        assertTrue(ArsSpellIntegrity.isIntact(
                payloadWithRecipe("ars_nouveau:glyph_projectile", "ars_nouveau:glyph_ignite")),
            "a recipe built entirely from registered glyphs must not be refused");
    }

    @Test
    void isIntact_isFalseWhenAnyGlyphIsGone() {
        assertFalse(ArsSpellIntegrity.isIntact(
                payloadWithRecipe("ars_nouveau:glyph_projectile", "some_addon:glyph_that_left")),
            "one unresolvable glyph is enough: Ars 5.x substitutes EffectBreak for it, so the "
                + "spell would still cast and would do something the player never built");
    }

    @Test
    void missingGlyphIds_isEmptyForAPayloadWithNoRecipe() {
        assertTrue(ArsSpellIntegrity.missingGlyphIds(new CompoundTag()).isEmpty(),
            "a payload with no recipe field has nothing to report");
        assertTrue(ArsSpellIntegrity.missingGlyphIds(null).isEmpty(),
            "a null payload must not throw");
    }

    @Test
    void missingGlyphIds_reportsMalformedIdsRatherThanThrowing() {
        assertEquals(List.of("NOT A VALID ID"),
            ArsSpellIntegrity.missingGlyphIds(payloadWithRecipe("NOT A VALID ID")),
            "an unparseable id is itself a missing glyph, not an exception to propagate into a "
                + "cast or a tooltip");
    }
}
