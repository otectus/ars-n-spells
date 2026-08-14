package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.util.ArsSpellIntegrity;
import com.otectus.arsnspells.util.SchoolResolver;
import com.otectus.arsnspells.util.SpellSchoolId;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Addon compatibility, exercised against real addon glyphs rather than synthetic ones.
 *
 * <p>Every test here self-skips when its addon is absent, so the default GameTest run is
 * unaffected. Enable with the opt-in gradle profiles:
 *
 * <pre>
 *   ./gradlew runGameTestServer -PwithArsElemental
 *   ./gradlew runGameTestServer -PwithTooManyGlyphs
 *   ./gradlew runGameTestServer -PwithIronsRuntimeGameTests -PwithArsElemental -PwithTooManyGlyphs
 * </pre>
 *
 * <p>The point is to falsify the previous "total compatibility" claim or earn it. A glyph from
 * an addon has to survive the full data path — serialize, integrity-check, deserialize, and
 * resolve to a school — and the school has to come from the addon's declared metadata rather
 * than from a lucky substring match on its registry path.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class AddonCompatGameTests {

    private static final String ARS_ELEMENTAL = "ars_elemental";
    private static final String TOO_MANY_GLYPHS = "toomanyglyphs";

    private AddonCompatGameTests() {}

    private static boolean loaded(String modid) {
        return ModList.get().isLoaded(modid);
    }

    /** Every registered glyph belonging to {@code modid}. */
    private static List<AbstractSpellPart> glyphsOf(String modid) {
        List<AbstractSpellPart> out = new ArrayList<>();
        GlyphRegistry.getSpellpartMap().forEach((id, part) -> {
            if (id != null && modid.equals(id.getNamespace()) && part != null) {
                out.add(part);
            }
        });
        return out;
    }

    /**
     * A spell built from real addon glyphs must serialize and come back byte-faithful: same
     * recipe length, same glyph ids, no silent drops. This is the round-trip the export →
     * bind → relog → cast path depends on.
     */
    private static void assertRoundTrips(GameTestHelper helper, String modid) {
        List<AbstractSpellPart> glyphs = glyphsOf(modid);
        if (glyphs.isEmpty()) {
            helper.fail(modid + " is loaded but registers no glyphs; the profile is misconfigured");
        }

        // Serialize each glyph on its own so one unserializable part cannot hide behind another.
        for (AbstractSpellPart glyph : glyphs) {
            Spell spell = new Spell(glyph);
            CompoundTag tag = spell.serialize();

            List<String> missing = ArsSpellIntegrity.missingGlyphIds(tag);
            if (!missing.isEmpty()) {
                helper.fail("glyph " + glyph.getRegistryName() + " does not survive its own "
                    + "serialization round-trip — integrity check reports missing: " + missing
                    + ". An exported spell using it would be rejected at bind time.");
            }

            Spell back = Spell.fromTag(tag);
            if (back.recipe.size() != spell.recipe.size()) {
                helper.fail("glyph " + glyph.getRegistryName() + " was dropped by deserialization: "
                    + spell.recipe.size() + " part(s) in, " + back.recipe.size() + " out");
            }
        }
    }

    /**
     * Schools must come from declared metadata, not from the substring heuristic.
     *
     * <p>Reported as a count rather than asserted per-glyph: an addon is free to leave a
     * utility glyph school-less, and demanding otherwise would make this test a liability on
     * every addon update. What would be a real regression is <em>zero</em> glyphs resolving
     * from metadata, which means the metadata path is not being consulted at all.
     */
    private static void assertSchoolsComeFromMetadata(GameTestHelper helper, String modid) {
        List<AbstractSpellPart> glyphs = glyphsOf(modid);
        int declared = 0;
        int resolved = 0;
        for (AbstractSpellPart glyph : glyphs) {
            boolean hasMetadata = glyph.spellSchools != null && !glyph.spellSchools.isEmpty();
            if (!hasMetadata) {
                continue;
            }
            declared++;
            // Resolve using ONLY the declared schools, with the registry path deliberately
            // replaced by one that matches no heuristic keyword. If the answer is still a real
            // school, it can only have come from the metadata.
            List<String> schoolIds = new ArrayList<>();
            glyph.spellSchools.forEach(s -> {
                if (s != null && s.getId() != null) {
                    schoolIds.add(s.getId());
                }
            });
            SpellSchoolId viaMetadata =
                SchoolResolver.resolve(modid + ":zzz_unmatchable_path", schoolIds);
            if (!viaMetadata.isGeneric()) {
                resolved++;
            }
        }
        if (declared == 0) {
            helper.fail(modid + " declares no spell schools on any glyph — either the addon "
                + "changed, or the profile loaded the wrong artifact");
        }
        if (resolved == 0) {
            helper.fail(modid + " declares schools on " + declared + " glyph(s) but none translate "
                + "to an ANS school. The Ars-school translation table has drifted from the addon's "
                + "vocabulary; add entries via ans_glyph_schools datapack overrides.");
        }
    }

    /** Every glyph resolves to a school without throwing, whatever its shape. */
    private static void assertResolutionIsTotal(GameTestHelper helper, String modid) {
        for (AbstractSpellPart glyph : glyphsOf(modid)) {
            SpellSchoolId school;
            try {
                school = SchoolResolver.resolve(glyph);
            } catch (Throwable t) {
                helper.fail("resolving a school for " + glyph.getRegistryName() + " threw "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
                return;
            }
            if (school == null) {
                helper.fail("school resolution returned null for " + glyph.getRegistryName()
                    + "; it must always yield a value, GENERIC at worst");
            }
        }
    }

    // ------------------------------------------------------------------
    // Ars Elemental
    // ------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void arsElemental_glyphsRoundTrip(GameTestHelper helper) {
        if (!loaded(ARS_ELEMENTAL)) {
            helper.succeed();
            return;
        }
        assertRoundTrips(helper, ARS_ELEMENTAL);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void arsElemental_schoolsResolveFromDeclaredMetadata(GameTestHelper helper) {
        if (!loaded(ARS_ELEMENTAL)) {
            helper.succeed();
            return;
        }
        assertSchoolsComeFromMetadata(helper, ARS_ELEMENTAL);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void arsElemental_everyGlyphResolvesWithoutThrowing(GameTestHelper helper) {
        if (!loaded(ARS_ELEMENTAL)) {
            helper.succeed();
            return;
        }
        assertResolutionIsTotal(helper, ARS_ELEMENTAL);
        helper.succeed();
    }

    /**
     * The named Ars Elemental effects from the audit brief, checked individually so a
     * regression names the spell that broke rather than a count.
     */
    @GameTest(template = "platform")
    public static void arsElemental_namedEffectsAreUsable(GameTestHelper helper) {
        if (!loaded(ARS_ELEMENTAL)) {
            helper.succeed();
            return;
        }
        // Verified against ars_elemental-1.20.1-0.6.8.0: the brief calls the first one
        // "Water Grave"; it is registered as glyph_watery_grave.
        String[] wanted = {"watery_grave", "discharge", "envenom", "spike", "spark"};
        List<String> found = new ArrayList<>();
        for (String name : wanted) {
            AbstractSpellPart part = findGlyph(ARS_ELEMENTAL, name);
            if (part == null) {
                continue; // renamed upstream; the sweep tests still cover it
            }
            found.add(name);
            CompoundTag tag = new Spell(part).serialize();
            if (!ArsSpellIntegrity.isIntact(tag)) {
                helper.fail("Ars Elemental " + name + " does not survive serialization intact");
            }
            if (SchoolResolver.resolve(part) == null) {
                helper.fail("Ars Elemental " + name + " resolved to a null school");
            }
        }
        // Requiring most-but-not-all keeps a single upstream rename from failing the build
        // while still catching wholesale drift (or the wrong artifact being loaded).
        if (found.size() < wanted.length - 1) {
            helper.fail("only found " + found + " of " + String.join(", ", wanted)
                + " among Ars Elemental's glyphs — the profile may have loaded an unexpected "
                + "artifact, or the addon renamed its effects");
        }
        helper.succeed();
    }

    /** First glyph of {@code modid} whose path contains {@code fragment}, or null. */
    private static AbstractSpellPart findGlyph(String modid, String fragment) {
        for (AbstractSpellPart candidate : glyphsOf(modid)) {
            ResourceLocation id = candidate.getRegistryName();
            if (id != null && id.getPath().contains(fragment)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The brief's "filter/control glyph before the real effect" case, with real addon glyphs.
     *
     * <p>Ars Elemental ships a family of filters ({@code glyph_aquatic_filter},
     * {@code glyph_not_undead_filter}, …). Because {@code AbstractFilter} extends
     * {@code AbstractEffect}, a recipe of Filter-then-Effect used to be classified by the
     * <em>filter</em> — wrong school, wrong cooldown category, wrong elemental scaling. This
     * asserts the effect after the filter is what decides.
     */
    @GameTest(template = "platform")
    public static void arsElemental_filterBeforeEffect_doesNotDecideTheSchool(GameTestHelper helper) {
        if (!loaded(ARS_ELEMENTAL)) {
            helper.succeed();
            return;
        }
        AbstractSpellPart filter = findGlyph(ARS_ELEMENTAL, "_filter");
        AbstractSpellPart effect = findGlyph(ARS_ELEMENTAL, "discharge");
        if (filter == null || effect == null) {
            helper.fail("expected a filter glyph and glyph_discharge in Ars Elemental 0.6.8.0");
        }

        SpellSchoolId effectAlone = SchoolResolver.resolve(effect);
        SpellSchoolId viaAnalysis = com.otectus.arsnspells.util.SpellAnalysis
            .analyze(java.util.List.of(filter, effect)).school();

        if (viaAnalysis != effectAlone) {
            helper.fail("a filter placed before the effect changed the resolved school: effect "
                + "alone = " + effectAlone + ", filter+effect = " + viaAnalysis + ". Filters "
                + "select targets; they must not decide what the spell IS.");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Too Many Glyphs
    // ------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void tooManyGlyphs_glyphsRoundTrip(GameTestHelper helper) {
        if (!loaded(TOO_MANY_GLYPHS)) {
            helper.succeed();
            return;
        }
        assertRoundTrips(helper, TOO_MANY_GLYPHS);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void tooManyGlyphs_everyGlyphResolvesWithoutThrowing(GameTestHelper helper) {
        if (!loaded(TOO_MANY_GLYPHS)) {
            helper.succeed();
            return;
        }
        assertResolutionIsTotal(helper, TOO_MANY_GLYPHS);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Both together
    // ------------------------------------------------------------------

    /**
     * A mixed-addon recipe is the case a single-addon test cannot cover: it proves the payload
     * carries glyph ids from several namespaces through one serialization without either
     * addon's parts being dropped.
     */
    @GameTest(template = "platform")
    public static void mixedAddonRecipe_survivesSerialization(GameTestHelper helper) {
        if (!loaded(ARS_ELEMENTAL) || !loaded(TOO_MANY_GLYPHS)) {
            helper.succeed();
            return;
        }
        List<AbstractSpellPart> elemental = glyphsOf(ARS_ELEMENTAL);
        List<AbstractSpellPart> tmg = glyphsOf(TOO_MANY_GLYPHS);
        if (elemental.isEmpty() || tmg.isEmpty()) {
            helper.fail("both addons are loaded but one registers no glyphs");
        }

        Spell mixed = new Spell(elemental.get(0), tmg.get(0));
        CompoundTag tag = mixed.serialize();

        List<String> missing = ArsSpellIntegrity.missingGlyphIds(tag);
        if (!missing.isEmpty()) {
            helper.fail("a mixed Ars Elemental + Too Many Glyphs recipe reports missing glyphs: "
                + missing);
        }
        Spell back = Spell.fromTag(tag);
        if (back.recipe.size() != mixed.recipe.size()) {
            helper.fail("a mixed-addon recipe lost parts in round-trip: " + mixed.recipe.size()
                + " in, " + back.recipe.size() + " out");
        }
        if (!CrossCastNbt.isArsCrossProxyId("ars_n_spells:ars_cross_1")) {
            helper.fail("sanity: proxy id detection must still work with addons loaded");
        }
        helper.succeed();
    }
}
