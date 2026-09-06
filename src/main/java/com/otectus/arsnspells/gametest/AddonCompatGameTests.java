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
    private static final String ARS_ZERO = "ars_zero";
    private static final String TOO_MANY_GLYPHS = "toomanyglyphs";

    private AddonCompatGameTests() {}

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
        if (OptionalModGate.skipIfAbsent(helper, ARS_ELEMENTAL)) {
            return;
        }
        assertRoundTrips(helper, ARS_ELEMENTAL);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void arsElemental_schoolsResolveFromDeclaredMetadata(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, ARS_ELEMENTAL)) {
            return;
        }
        assertSchoolsComeFromMetadata(helper, ARS_ELEMENTAL);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void arsElemental_everyGlyphResolvesWithoutThrowing(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, ARS_ELEMENTAL)) {
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
        if (OptionalModGate.skipIfAbsent(helper, ARS_ELEMENTAL)) {
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

    /** The glyph registered as exactly {@code modid:path}, or null if the addon never had it. */
    private static AbstractSpellPart glyphById(String modid, String path) {
        return GlyphRegistry.getSpellpartMap().get(new ResourceLocation(modid, path));
    }

    /**
     * Asserts one named glyph's school.
     *
     * <p>Skips silently when the glyph is absent from the registry — an addon is allowed to
     * rename or drop a glyph, and the sweep tests still cover whatever it does ship. Being
     * present and resolving to the wrong school is a real regression and fails.
     */
    private static void assertGlyphSchool(GameTestHelper helper, String modid, String path,
                                          SpellSchoolId expected) {
        AbstractSpellPart part = glyphById(modid, path);
        if (part == null) {
            return;
        }
        SpellSchoolId actual = SchoolResolver.resolve(part);
        if (actual != expected) {
            helper.fail(modid + ":" + path + " resolved to " + actual + ", expected " + expected
                + ". School resolution has drifted from the addon's declared metadata or from "
                + "the explicit override table in SchoolMappings.");
        }
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
        if (OptionalModGate.skipIfAbsent(helper, ARS_ELEMENTAL)) {
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

    /**
     * Ars Elemental's undead/charm glyphs declare the Ars school {@code necromancy}, which had
     * no entry in the translation table and so resolved GENERIC — no affinity, no scaling, no
     * cooldown category. They belong to ELDRITCH.
     */
    @GameTest(template = "platform")
    public static void arsElemental_necromancyGlyphsResolve(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, ARS_ELEMENTAL)) {
            return;
        }
        assertGlyphSchool(helper, ARS_ELEMENTAL, "glyph_phantom_grasp", SpellSchoolId.ELDRITCH);
        assertGlyphSchool(helper, ARS_ELEMENTAL, "glyph_charm", SpellSchoolId.ELDRITCH);
        // Life Link drains to heal; it feeds the Blood track rather than the undead one.
        assertGlyphSchool(helper, ARS_ELEMENTAL, "glyph_life_link", SpellSchoolId.BLOOD);
        helper.succeed();
    }

    /**
     * Filters select targets. Every one of them must be GENERIC: the name heuristic used to
     * read {@code aquatic_filter} as ICE, {@code fiery_filter} as FIRE and
     * {@code summon_filter} as EVOCATION, handing a school to a glyph that has no payload.
     */
    @GameTest(template = "platform")
    public static void arsElemental_filtersResolveGeneric(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, ARS_ELEMENTAL)) {
            return;
        }
        // Verified against ars_elemental-1.20.1-0.6.8.0.
        String[] bases = {"aerial", "aquatic", "fiery", "insect", "summon", "undead"};
        int checked = 0;
        for (String base : bases) {
            for (String path : new String[] {"glyph_" + base + "_filter",
                                             "glyph_not_" + base + "_filter"}) {
                if (glyphById(ARS_ELEMENTAL, path) == null) {
                    continue;
                }
                checked++;
                assertGlyphSchool(helper, ARS_ELEMENTAL, path, SpellSchoolId.GENERIC);
            }
        }
        if (checked == 0) {
            helper.fail("Ars Elemental is loaded but registers none of its filter glyphs; the "
                + "profile loaded an unexpected artifact");
        }
        // Propagators are chained forms, not payloads, and must not decide a school either.
        assertGlyphSchool(helper, ARS_ELEMENTAL, "glyph_propagator_arc", SpellSchoolId.GENERIC);
        assertGlyphSchool(helper, ARS_ELEMENTAL, "glyph_propagator_homing", SpellSchoolId.GENERIC);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Ars Zero
    // ------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void arsZero_glyphsRoundTrip(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, ARS_ZERO)) {
            return;
        }
        assertRoundTrips(helper, ARS_ZERO);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void arsZero_everyGlyphResolvesWithoutThrowing(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, ARS_ZERO)) {
            return;
        }
        assertResolutionIsTotal(helper, ARS_ZERO);
        helper.succeed();
    }

    /**
     * Ars Zero's effects, pinned one by one. Verified against ars_zero-1.20.1-2.0.2HOTFIX; note
     * its glyph ids carry no {@code glyph_} prefix, so any code that strips one has to be
     * guarded rather than offset-based.
     */
    @GameTest(template = "platform")
    public static void arsZero_schoolsResolveFromDeclaredMetadata(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, ARS_ZERO)) {
            return;
        }
        assertSchoolsComeFromMetadata(helper, ARS_ZERO);

        assertGlyphSchool(helper, ARS_ZERO, "effect_geometrize", SpellSchoolId.NATURE);
        assertGlyphSchool(helper, ARS_ZERO, "push_effect", SpellSchoolId.LIGHTNING);
        assertGlyphSchool(helper, ARS_ZERO, "conjure_voxel_effect", SpellSchoolId.ENDER);
        assertGlyphSchool(helper, ARS_ZERO, "zero_gravity_effect", SpellSchoolId.ENDER);
        assertGlyphSchool(helper, ARS_ZERO, "effect_beam", SpellSchoolId.ENDER);
        assertGlyphSchool(helper, ARS_ZERO, "effect_conjure_blight", SpellSchoolId.ELDRITCH);
        assertGlyphSchool(helper, ARS_ZERO, "conjure_arcane_shield_effect", SpellSchoolId.HOLY);
        helper.succeed();
    }

    /**
     * Ars Zero's multi-phase glyphs are control flow: they anchor, select, sustain and discard
     * an ongoing spell. They declare MANIPULATION, which would translate to a real school and
     * earn affinity for casting nothing. They have to be GENERIC.
     */
    @GameTest(template = "platform")
    public static void arsZero_controlFlowGlyphsAreGeneric(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, ARS_ZERO)) {
            return;
        }
        String[] controlFlow = {"anchor_effect", "select_effect", "sustain_effect",
                                "discard_effect", "effect_convergence", "enlarge_effect"};
        for (String path : controlFlow) {
            assertGlyphSchool(helper, ARS_ZERO, path, SpellSchoolId.GENERIC);
        }
        // Forms and augments are shape, not payload.
        assertGlyphSchool(helper, ARS_ZERO, "near_form", SpellSchoolId.GENERIC);
        assertGlyphSchool(helper, ARS_ZERO, "temporal_context_form", SpellSchoolId.GENERIC);
        String[] augments = {"augment_amplify_two", "augment_amplify_three", "augment_aoe_two",
                             "augment_aoe_three", "augment_cube", "augment_flatten",
                             "augment_hollow", "augment_sphere"};
        for (String path : augments) {
            assertGlyphSchool(helper, ARS_ZERO, path, SpellSchoolId.GENERIC);
        }
        helper.succeed();
    }

    /**
     * The Ars Zero analogue of the filter case: a form and an augment sit in front of the
     * effect, and the effect still has to be what decides the school.
     */
    @GameTest(template = "platform")
    public static void arsZero_augmentsAndFormsDoNotDecideTheSchool(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, ARS_ZERO)) {
            return;
        }
        AbstractSpellPart form = glyphById(ARS_ZERO, "near_form");
        AbstractSpellPart augment = glyphById(ARS_ZERO, "augment_amplify_two");
        AbstractSpellPart effect = glyphById(ARS_ZERO, "effect_geometrize");
        if (form == null || augment == null || effect == null) {
            helper.fail("expected near_form, augment_amplify_two and effect_geometrize in "
                + "Ars Zero 2.0.2");
        }

        SpellSchoolId viaAnalysis = com.otectus.arsnspells.util.SpellAnalysis
            .analyze(java.util.List.of(form, augment, effect)).school();

        if (viaAnalysis != SpellSchoolId.NATURE) {
            helper.fail("near_form + augment_amplify_two + effect_geometrize analysed to "
                + viaAnalysis + ", expected NATURE. Forms and augments shape a spell; they must "
                + "not decide what it IS.");
        }
        helper.succeed();
    }

    /**
     * The named Ars Zero effects, checked individually so a regression names the glyph that
     * broke rather than a count.
     */
    @GameTest(template = "platform")
    public static void arsZero_namedEffectsAreUsable(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, ARS_ZERO)) {
            return;
        }
        String[] wanted = {"effect_geometrize", "push_effect", "effect_beam",
                           "conjure_voxel_effect", "effect_conjure_blight"};
        List<String> found = new ArrayList<>();
        for (String path : wanted) {
            AbstractSpellPart part = glyphById(ARS_ZERO, path);
            if (part == null) {
                continue; // renamed upstream; the sweep tests still cover it
            }
            found.add(path);
            CompoundTag tag = new Spell(part).serialize();
            if (!ArsSpellIntegrity.isIntact(tag)) {
                helper.fail("Ars Zero " + path + " does not survive serialization intact");
            }
            if (SchoolResolver.resolve(part) == null) {
                helper.fail("Ars Zero " + path + " resolved to a null school");
            }
        }
        if (found.size() < wanted.length - 1) {
            helper.fail("only found " + found + " of " + String.join(", ", wanted)
                + " among Ars Zero's glyphs — the profile may have loaded an unexpected "
                + "artifact, or the addon renamed its effects");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Too Many Glyphs
    // ------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void tooManyGlyphs_glyphsRoundTrip(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, TOO_MANY_GLYPHS)) {
            return;
        }
        assertRoundTrips(helper, TOO_MANY_GLYPHS);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void tooManyGlyphs_everyGlyphResolvesWithoutThrowing(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, TOO_MANY_GLYPHS)) {
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
        if (OptionalModGate.skipIfAbsent(helper, ARS_ELEMENTAL) || OptionalModGate.skipIfAbsent(helper, TOO_MANY_GLYPHS)) {
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

    /**
     * Ars Elemental and Ars Zero in one recipe. The two addons name their glyphs differently
     * (Ars Elemental prefixes {@code glyph_}, Ars Zero does not), so this is also the case that
     * would expose any id handling that assumes a fixed prefix length.
     */
    @GameTest(template = "platform")
    public static void mixedArsElementalArsZero_recipe_survivesSerialization(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, ARS_ELEMENTAL) || OptionalModGate.skipIfAbsent(helper, ARS_ZERO)) {
            return;
        }
        List<AbstractSpellPart> elemental = glyphsOf(ARS_ELEMENTAL);
        List<AbstractSpellPart> zero = glyphsOf(ARS_ZERO);
        if (elemental.isEmpty() || zero.isEmpty()) {
            helper.fail("both addons are loaded but one registers no glyphs");
        }

        Spell mixed = new Spell(elemental.get(0), zero.get(0));
        CompoundTag tag = mixed.serialize();

        List<String> missing = ArsSpellIntegrity.missingGlyphIds(tag);
        if (!missing.isEmpty()) {
            helper.fail("a mixed Ars Elemental + Ars Zero recipe reports missing glyphs: "
                + missing);
        }
        Spell back = Spell.fromTag(tag);
        if (back.recipe.size() != mixed.recipe.size()) {
            helper.fail("a mixed Ars Elemental + Ars Zero recipe lost parts in round-trip: "
                + mixed.recipe.size() + " in, " + back.recipe.size() + " out");
        }
        helper.succeed();
    }
}
