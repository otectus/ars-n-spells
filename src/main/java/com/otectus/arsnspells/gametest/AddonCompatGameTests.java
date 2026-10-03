package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.common.items.Glyph;
import com.otectus.arsnspells.compat.CompatIds;
import com.otectus.arsnspells.compat.ModPresence;
import com.otectus.arsnspells.registry.ModTags;
import com.otectus.arsnspells.util.ArsSpellIntegrity;
import com.otectus.arsnspells.util.SchoolResolver;
import com.otectus.arsnspells.util.SpellSchoolId;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Addon compatibility, exercised against real addon glyphs rather than synthetic ones.
 *
 * <p>Every test here self-skips when its addon is absent, so the default GameTest run is
 * unaffected. Enable with the opt-in profiles:
 *
 * <pre>
 *   ./gradlew runGameTestServer -PwithArsElemental
 *   ./gradlew runGameTestServer -PwithArsZero       # Ars Zero requires Ars Elemental; both load
 *   ./gradlew runGameTestServer -PwithArsElemancy   # Ars Elemancy likewise requires Elemental
 * </pre>
 *
 * <p>The point is to falsify the "total addon compatibility" claim or earn it. A glyph from an
 * addon has to survive the full data path - serialize, integrity-check, deserialize - and its
 * school has to come from the addon's declared metadata rather than from a lucky substring
 * match on its registry path. The three generic checks run once per addon.
 *
 * <p>Ars Zero adds checks of its own, because it is the first addon with a caster of its own.
 * ANS's shared-pool mana accounting lives in two mixins on {@link SpellResolver}
 * ({@code canCast} and {@code expendMana}); Ars Zero's Spell Staff resolves through its own
 * {@code SpellResolver} subclasses, which inherit those injections only for as long as they do
 * not override the two methods. That is a fact about the addon's bytecode, so it is asserted
 * reflectively here rather than assumed. Its beam and voxel entities drain mana through
 * {@code IManaCap.removeMana}, which {@code MixinManaCapability} intercepts - no test needed.
 * Its multi-phase control glyphs only work inside the staff's phase context, so they are tagged
 * {@code #ars_n_spells:cross_cast_blacklist} and the tag is asserted to bite.
 *
 * <p>Addon classes are only ever named as strings: an import of an {@code ars_zero} type would
 * make this whole class fail verification on the addon-less default run.
 *
 * <p>The 1.20.1 line also carried a Too Many Glyphs profile. That mod has no 1.21.1 release, so
 * per the standing rule on mods without a 1.21.1 build there is no counterpart here.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class AddonCompatGameTests {

    /** Ars Zero's {@code SpellResolver} subclasses, as of 2.0.2. Class names, never imports. */
    private static final String[] ARS_ZERO_RESOLVERS = {
        "com.github.ars_zero.common.spell.WrappedSpellResolver",
        "com.github.ars_zero.api.spell.MobSpellResolver",
    };

    private AddonCompatGameTests() {}

    private static boolean loaded(String modid) {
        return ModPresence.isLoaded(modid);
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

    /** Serialize one glyph the way an inscribed item stores it. */
    private static CompoundTag serialize(AbstractSpellPart glyph) {
        Spell spell = new Spell().setRecipe(List.of(glyph));
        Tag encoded = Spell.CODEC.codec().encodeStart(NbtOps.INSTANCE, spell).result().orElse(null);
        return encoded instanceof CompoundTag tag ? tag : new CompoundTag();
    }

    // ---- Generic per-addon checks ----

    private static void glyphsRoundTrip(GameTestHelper helper, String modid) {
        if (OptionalModGate.skipIfAbsent(helper, modid)) {
            return;
        }
        List<AbstractSpellPart> glyphs = glyphsOf(modid);
        if (glyphs.isEmpty()) {
            helper.fail(modid + " is loaded but registers no glyphs; the profile is "
                + "misconfigured and this suite is proving nothing");
            return;
        }

        // One glyph at a time, so an unserializable part cannot hide behind a working one.
        for (AbstractSpellPart glyph : glyphs) {
            CompoundTag tag = serialize(glyph);

            List<String> missing = ArsSpellIntegrity.missingGlyphIds(tag);
            if (!missing.isEmpty()) {
                helper.fail("glyph " + glyph.getRegistryName() + " does not survive its own "
                    + "serialization round trip - the integrity check reports missing: " + missing
                    + ". An exported spell using it would be rejected at bind time.");
                return;
            }

            Spell back = Spell.CODEC.codec().parse(NbtOps.INSTANCE, tag).result().orElse(null);
            if (back == null) {
                helper.fail("glyph " + glyph.getRegistryName() + " did not deserialize at all");
                return;
            }
            int parts = 0;
            for (AbstractSpellPart ignored : back.recipe()) {
                parts++;
            }
            if (parts != 1) {
                helper.fail("glyph " + glyph.getRegistryName() + " was dropped by "
                    + "deserialization: 1 part in, " + parts + " out");
                return;
            }
        }
        helper.succeed();
    }

    private static void schoolsResolveFromDeclaredMetadata(GameTestHelper helper, String modid) {
        if (OptionalModGate.skipIfAbsent(helper, modid)) {
            return;
        }
        List<AbstractSpellPart> glyphs = glyphsOf(modid);
        int declared = 0;
        int resolved = 0;

        for (AbstractSpellPart glyph : glyphs) {
            if (glyph.spellSchools == null || glyph.spellSchools.isEmpty()) {
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
            helper.fail(modid + " declares no spell schools on any glyph - either the "
                + "addon changed, or the profile loaded the wrong artifact");
            return;
        }
        if (resolved == 0) {
            helper.fail(modid + " declares schools on " + declared + " glyph(s) but none "
                + "translate to an ANS school. The Ars-school translation table has drifted from "
                + "the addon's vocabulary.");
            return;
        }
        helper.succeed();
    }

    private static void everyGlyphResolvesWithoutThrowing(GameTestHelper helper, String modid) {
        if (OptionalModGate.skipIfAbsent(helper, modid)) {
            return;
        }
        for (AbstractSpellPart glyph : glyphsOf(modid)) {
            try {
                SpellSchoolId school = SchoolResolver.resolve(glyph);
                if (school == null) {
                    helper.fail("school resolution returned null for " + glyph.getRegistryName()
                        + "; it must return the generic school rather than null");
                    return;
                }
            } catch (Throwable t) {
                helper.fail("resolving a school for " + glyph.getRegistryName() + " threw: " + t);
                return;
            }
        }
        helper.succeed();
    }

    // ---- Ars Elemental ----

    @GameTest(template = "platform")
    public static void arsElemental_glyphsRoundTrip(GameTestHelper helper) {
        glyphsRoundTrip(helper, CompatIds.ARS_ELEMENTAL);
    }

    @GameTest(template = "platform")
    public static void arsElemental_schoolsResolveFromDeclaredMetadata(GameTestHelper helper) {
        schoolsResolveFromDeclaredMetadata(helper, CompatIds.ARS_ELEMENTAL);
    }

    @GameTest(template = "platform")
    public static void arsElemental_everyGlyphResolvesWithoutThrowing(GameTestHelper helper) {
        everyGlyphResolvesWithoutThrowing(helper, CompatIds.ARS_ELEMENTAL);
    }

    /**
     * The named Ars Elemental effects from the audit brief, checked individually so a
     * regression names the spell that broke rather than a count. (Forge parity.)
     */
    @GameTest(template = "platform")
    public static void arsElemental_namedEffectsAreUsable(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.ARS_ELEMENTAL)) {
            return;
        }
        namedEffectsAreUsable(helper, CompatIds.ARS_ELEMENTAL, false,
            "watery_grave", "discharge", "envenom", "spike", "spark");
    }

    /**
     * A filter placed before the real effect must not decide the school: Filter-then-Effect
     * used to be classified by the filter. (Forge parity.)
     */
    @GameTest(template = "platform")
    public static void arsElemental_filterBeforeEffect_doesNotDecideTheSchool(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.ARS_ELEMENTAL)) {
            return;
        }
        AbstractSpellPart filter = findGlyph(CompatIds.ARS_ELEMENTAL, "_filter");
        AbstractSpellPart effect = findGlyph(CompatIds.ARS_ELEMENTAL, "discharge");
        if (filter == null || effect == null) {
            helper.fail("expected a filter glyph and glyph_discharge in Ars Elemental");
            return;
        }
        SpellSchoolId effectAlone = SchoolResolver.resolve(effect);
        SpellSchoolId viaAnalysis = SpellSchoolId.fromId(com.otectus.arsnspells.util.SpellAnalysis
            .analyze(List.of(filter, effect)).dominantSchool());
        if (viaAnalysis != effectAlone) {
            helper.fail("a filter placed before the effect changed the resolved school: effect "
                + "alone = " + effectAlone + ", filter+effect = " + viaAnalysis + ". Filters "
                + "select targets; they must not decide what the spell IS.");
            return;
        }
        helper.succeed();
    }

    /**
     * Ars Elemental's undead/charm glyphs declare the Ars school {@code necromancy}; they belong
     * to ELDRITCH. Life Link drains to heal and feeds the Blood track. (Forge parity.)
     */
    @GameTest(template = "platform")
    public static void arsElemental_necromancyGlyphsResolve(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.ARS_ELEMENTAL)) {
            return;
        }
        assertGlyphSchool(helper, CompatIds.ARS_ELEMENTAL, "glyph_phantom_grasp", SpellSchoolId.ELDRITCH);
        assertGlyphSchool(helper, CompatIds.ARS_ELEMENTAL, "glyph_charm", SpellSchoolId.ELDRITCH);
        assertGlyphSchool(helper, CompatIds.ARS_ELEMENTAL, "glyph_life_link", SpellSchoolId.BLOOD);
        helper.succeed();
    }

    /**
     * Filters select targets. Every one of them must be GENERIC; propagators are chained forms
     * and must not decide a school either. (Forge parity.)
     */
    @GameTest(template = "platform")
    public static void arsElemental_filtersResolveGeneric(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.ARS_ELEMENTAL)) {
            return;
        }
        String[] bases = {"aerial", "aquatic", "fiery", "insect", "summon", "undead"};
        int checked = 0;
        for (String base : bases) {
            for (String path : new String[] {"glyph_" + base + "_filter",
                                             "glyph_not_" + base + "_filter"}) {
                if (glyphById(CompatIds.ARS_ELEMENTAL, path) == null) {
                    continue;
                }
                checked++;
                assertGlyphSchool(helper, CompatIds.ARS_ELEMENTAL, path, SpellSchoolId.GENERIC);
            }
        }
        if (checked == 0) {
            helper.fail("Ars Elemental is loaded but registers none of its filter glyphs; the "
                + "profile loaded an unexpected artifact");
            return;
        }
        assertGlyphSchool(helper, CompatIds.ARS_ELEMENTAL, "glyph_propagator_arc", SpellSchoolId.GENERIC);
        assertGlyphSchool(helper, CompatIds.ARS_ELEMENTAL, "glyph_propagator_homing", SpellSchoolId.GENERIC);
        helper.succeed();
    }

    /**
     * Ars Elemental and Ars Zero in one recipe. The two addons name their glyphs differently
     * (Ars Elemental prefixes {@code glyph_}, Ars Zero does not), so this is also the case that
     * would expose any id handling that assumes a fixed prefix length. (Forge parity.)
     */
    @GameTest(template = "platform")
    public static void mixedArsElementalArsZero_recipe_survivesSerialization(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.ARS_ELEMENTAL)
                || OptionalModGate.skipIfAbsent(helper, CompatIds.ARS_ZERO)) {
            return;
        }
        List<AbstractSpellPart> elemental = glyphsOf(CompatIds.ARS_ELEMENTAL);
        List<AbstractSpellPart> zero = glyphsOf(CompatIds.ARS_ZERO);
        if (elemental.isEmpty() || zero.isEmpty()) {
            helper.fail("both addons are loaded but one registers no glyphs");
            return;
        }
        Spell mixed = new Spell().setRecipe(List.of(elemental.get(0), zero.get(0)));
        Tag encoded = Spell.CODEC.codec().encodeStart(NbtOps.INSTANCE, mixed).result().orElse(null);
        CompoundTag tag = encoded instanceof CompoundTag compound ? compound : new CompoundTag();
        List<String> missing = ArsSpellIntegrity.missingGlyphIds(tag);
        if (!missing.isEmpty()) {
            helper.fail("a mixed Ars Elemental + Ars Zero recipe reports missing glyphs: " + missing);
            return;
        }
        Spell back = Spell.CODEC.codec().parse(NbtOps.INSTANCE, tag).result().orElse(null);
        int parts = 0;
        if (back != null) {
            for (AbstractSpellPart ignored : back.recipe()) {
                parts++;
            }
        }
        if (parts != 2) {
            helper.fail("a mixed Ars Elemental + Ars Zero recipe lost parts in round-trip: 2 in, "
                + parts + " out");
            return;
        }
        helper.succeed();
    }

    // ---- Ars Zero ----

    @GameTest(template = "platform")
    public static void arsZero_glyphsRoundTrip(GameTestHelper helper) {
        glyphsRoundTrip(helper, CompatIds.ARS_ZERO);
    }

    /**
     * Ars Zero's effects, pinned one by one (Forge parity), then the generic metadata sweep.
     * Its glyph ids carry no {@code glyph_} prefix, so any code that strips one has to be
     * guarded rather than offset-based.
     */
    @GameTest(template = "platform")
    public static void arsZero_schoolsResolveFromDeclaredMetadata(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.ARS_ZERO)) {
            return;
        }
        assertGlyphSchool(helper, CompatIds.ARS_ZERO, "effect_geometrize", SpellSchoolId.NATURE);
        assertGlyphSchool(helper, CompatIds.ARS_ZERO, "push_effect", SpellSchoolId.LIGHTNING);
        assertGlyphSchool(helper, CompatIds.ARS_ZERO, "conjure_voxel_effect", SpellSchoolId.ENDER);
        assertGlyphSchool(helper, CompatIds.ARS_ZERO, "zero_gravity_effect", SpellSchoolId.ENDER);
        assertGlyphSchool(helper, CompatIds.ARS_ZERO, "effect_beam", SpellSchoolId.ENDER);
        assertGlyphSchool(helper, CompatIds.ARS_ZERO, "effect_conjure_blight", SpellSchoolId.ELDRITCH);
        assertGlyphSchool(helper, CompatIds.ARS_ZERO, "conjure_arcane_shield_effect", SpellSchoolId.HOLY);
        schoolsResolveFromDeclaredMetadata(helper, CompatIds.ARS_ZERO);
    }

    /**
     * Ars Zero's multi-phase glyphs are control flow: they anchor, select, sustain and discard
     * an ongoing spell. They declare MANIPULATION, which would translate to a real school and
     * earn affinity for casting nothing. They have to be GENERIC. (Forge parity.)
     */
    @GameTest(template = "platform")
    public static void arsZero_controlFlowGlyphsAreGeneric(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.ARS_ZERO)) {
            return;
        }
        String[] controlFlow = {"anchor_effect", "select_effect", "sustain_effect",
                                "discard_effect", "effect_convergence", "enlarge_effect"};
        for (String path : controlFlow) {
            assertGlyphSchool(helper, CompatIds.ARS_ZERO, path, SpellSchoolId.GENERIC);
        }
        // Forms and augments are shape, not payload.
        assertGlyphSchool(helper, CompatIds.ARS_ZERO, "near_form", SpellSchoolId.GENERIC);
        assertGlyphSchool(helper, CompatIds.ARS_ZERO, "temporal_context_form", SpellSchoolId.GENERIC);
        String[] augments = {"augment_amplify_two", "augment_amplify_three", "augment_aoe_two",
                             "augment_aoe_three", "augment_cube", "augment_flatten",
                             "augment_hollow", "augment_sphere"};
        for (String path : augments) {
            assertGlyphSchool(helper, CompatIds.ARS_ZERO, path, SpellSchoolId.GENERIC);
        }
        helper.succeed();
    }

    /**
     * The Ars Zero analogue of the filter case: a form and an augment sit in front of the
     * effect, and the effect still has to be what decides the school. (Forge parity.)
     */
    @GameTest(template = "platform")
    public static void arsZero_augmentsAndFormsDoNotDecideTheSchool(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.ARS_ZERO)) {
            return;
        }
        AbstractSpellPart form = glyphById(CompatIds.ARS_ZERO, "near_form");
        AbstractSpellPart augment = glyphById(CompatIds.ARS_ZERO, "augment_amplify_two");
        AbstractSpellPart effect = glyphById(CompatIds.ARS_ZERO, "effect_geometrize");
        if (form == null || augment == null || effect == null) {
            helper.fail("expected near_form, augment_amplify_two and effect_geometrize in "
                + "Ars Zero 2.0.2");
            return;
        }
        SpellSchoolId viaAnalysis = SpellSchoolId.fromId(com.otectus.arsnspells.util.SpellAnalysis
            .analyze(List.of(form, augment, effect)).dominantSchool());
        if (viaAnalysis != SpellSchoolId.NATURE) {
            helper.fail("near_form + augment_amplify_two + effect_geometrize analysed to "
                + viaAnalysis + ", expected NATURE. Forms and augments shape a spell; they must "
                + "not decide what it IS.");
            return;
        }
        helper.succeed();
    }

    /**
     * The named Ars Zero effects, checked individually so a regression names the glyph that
     * broke rather than a count. (Forge parity.)
     */
    @GameTest(template = "platform")
    public static void arsZero_namedEffectsAreUsable(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.ARS_ZERO)) {
            return;
        }
        namedEffectsAreUsable(helper, CompatIds.ARS_ZERO, true, "effect_geometrize", "push_effect",
            "effect_beam", "conjure_voxel_effect", "effect_conjure_blight");
    }

    // ---- Named-glyph helpers (the same assertions as the Forge 1.20.1 suite) ----

    /** First glyph of {@code modid} whose path contains {@code fragment}, or null. */
    private static AbstractSpellPart findGlyph(String modid, String fragment) {
        for (AbstractSpellPart candidate : glyphsOf(modid)) {
            var id = candidate.getRegistryName();
            if (id != null && id.getPath().contains(fragment)) {
                return candidate;
            }
        }
        return null;
    }

    /** The glyph registered as exactly {@code modid:path}, or null if the addon never had it. */
    private static AbstractSpellPart glyphById(String modid, String path) {
        return GlyphRegistry.getSpellpartMap().get(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(modid, path));
    }

    /**
     * Asserts one named glyph's school. Skips silently when the glyph is absent from the
     * registry; being present and resolving to the wrong school is a real regression and fails.
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

    /** Each named glyph (exact id, or a path fragment) survives serialization and resolves. */
    private static void namedEffectsAreUsable(GameTestHelper helper, String modid, boolean exactIds,
                                              String... wanted) {
        List<String> found = new ArrayList<>();
        for (String name : wanted) {
            AbstractSpellPart part = exactIds ? glyphById(modid, name) : findGlyph(modid, name);
            if (part == null) {
                continue; // renamed upstream; the sweep tests still cover it
            }
            found.add(name);
            if (!ArsSpellIntegrity.missingGlyphIds(serialize(part)).isEmpty()) {
                helper.fail(modid + " " + name + " does not survive serialization intact");
                return;
            }
            if (SchoolResolver.resolve(part) == null) {
                helper.fail(modid + " " + name + " resolved to a null school");
                return;
            }
        }
        if (found.size() < wanted.length - 1) {
            helper.fail("only found " + found + " of " + String.join(", ", wanted)
                + " among " + modid + "'s glyphs - the profile may have loaded an unexpected "
                + "artifact, or the addon renamed its effects");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void arsZero_everyGlyphResolvesWithoutThrowing(GameTestHelper helper) {
        everyGlyphResolvesWithoutThrowing(helper, CompatIds.ARS_ZERO);
    }

    /** Ars Zero 2.0.2 requires Ars Elemental; a Zero-only classpath is a broken profile. */
    @GameTest(template = "platform")
    public static void arsZero_profileIsComplete(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.ARS_ZERO)) {
            return;
        }
        if (!loaded(CompatIds.ARS_ELEMENTAL)) {
            helper.fail("ars_zero is loaded without ars_elemental, which it declares as a "
                + "required dependency. The -PwithArsZero profile must also pull Ars Elemental.");
            return;
        }
        helper.succeed();
    }

    /**
     * The two mana mixins inject into {@code SpellResolver.canCast} and
     * {@code SpellResolver.expendMana}. A subclass that overrides either one silently takes
     * every staff cast out of the shared pool, so an override here is a failure, not a warning.
     */
    @GameTest(template = "platform")
    public static void arsZero_resolversInheritManaHooks(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.ARS_ZERO)) {
            return;
        }
        for (String name : ARS_ZERO_RESOLVERS) {
            Class<?> resolver;
            try {
                resolver = Class.forName(name);
            } catch (ClassNotFoundException e) {
                helper.fail("Ars Zero no longer ships " + name + "; the addon changed shape and "
                    + "its mana path has to be re-verified against MixinSpellResolverPreCast and "
                    + "MixinSpellResolverMana");
                return;
            }
            if (!SpellResolver.class.isAssignableFrom(resolver)) {
                helper.fail(name + " no longer extends SpellResolver, so ANS's mana mixins do not "
                    + "apply to Spell Staff casts at all");
                return;
            }
            if (declares(resolver, "canCast", LivingEntity.class)) {
                helper.fail(name + " overrides canCast(LivingEntity): MixinSpellResolverPreCast is "
                    + "bypassed for Spell Staff casts, so shared-pool mana is never validated");
                return;
            }
            if (declares(resolver, "expendMana")) {
                helper.fail(name + " overrides expendMana(): MixinSpellResolverMana is bypassed for "
                    + "Spell Staff casts, so shared-pool mana is never deducted");
                return;
            }
        }
        helper.succeed();
    }

    // ---- Ars Elemancy ----

    /**
     * Ars Elemancy is equipment-only: armor, bangles and elemental foci. It registers no glyphs
     * at all - its {@code ArsNouveauRegistry.registerGlyphs()} body is empty, and the compound
     * schools it declares (tempest, cinder, silt, mire, vapor, lava) are used for item matching
     * only and are never attached to a spell part. So the two glyph-population suites
     * ({@code glyphsRoundTrip}, {@code schoolsResolveFromDeclaredMetadata}) deliberately are NOT
     * wired for it: both fail by design on an empty glyph list, and running them here would
     * manufacture a false failure. Do not "fix" that by copying the other two addons.
     *
     * <p>What remains: the resolver check, which passes trivially today and starts guarding the
     * moment Elemancy ever does add a glyph; the dependency check, because Elemancy hard-requires
     * Ars Elemental; and an item-registry check, which is what actually proves the profile loaded
     * the right artifact.
     */
    @GameTest(template = "platform")
    public static void arsElemancy_everyGlyphResolvesWithoutThrowing(GameTestHelper helper) {
        everyGlyphResolvesWithoutThrowing(helper, CompatIds.ARS_ELEMANCY);
    }

    /** Ars Elemancy 1.18.3 requires Ars Elemental; an Elemancy-only classpath is a broken profile. */
    @GameTest(template = "platform")
    public static void arsElemancy_profileIsComplete(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.ARS_ELEMANCY)) {
            return;
        }
        if (!loaded(CompatIds.ARS_ELEMENTAL)) {
            helper.fail("ars_elemancy is loaded without ars_elemental, which it declares as a "
                + "required dependency. The -PwithArsElemancy profile must also pull Ars Elemental.");
            return;
        }
        helper.succeed();
    }

    /**
     * Elemancy contributes items, not glyphs, so its items are the only evidence the profile
     * resolved a real artifact rather than an empty or wrong one.
     */
    @GameTest(template = "platform")
    public static void arsElemancy_registersItems(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.ARS_ELEMANCY)) {
            return;
        }
        boolean any = BuiltInRegistries.ITEM.keySet().stream()
            .anyMatch(id -> CompatIds.ARS_ELEMANCY.equals(id.getNamespace()));
        if (!any) {
            helper.fail("ars_elemancy is loaded but registers no items; the profile resolved the "
                + "wrong artifact and this suite is proving nothing");
            return;
        }
        helper.succeed();
    }

    private static boolean declares(Class<?> type, String method, Class<?>... params) {
        try {
            type.getDeclaredMethod(method, params);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Every glyph item in {@code #ars_n_spells:cross_cast_blacklist} must be reported by both
     * integrity overloads. Also proves the shipped tag still names real Ars Zero glyphs: an
     * empty tag while Zero is loaded means the ids drifted and the blacklist is silently dead.
     */
    @GameTest(template = "platform")
    public static void arsZero_blacklistedGlyphsAreRejected(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.ARS_ZERO)) {
            return;
        }
        List<AbstractSpellPart> tagged = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(ModTags.CROSS_CAST_BLACKLIST).ifPresent(named -> {
            for (Holder<Item> holder : named) {
                if (holder.value() instanceof Glyph glyph && glyph.spellPart != null
                    && CompatIds.ARS_ZERO.equals(glyph.spellPart.getRegistryName().getNamespace())) {
                    tagged.add(glyph.spellPart);
                }
            }
        });
        if (tagged.isEmpty()) {
            helper.fail("#ars_n_spells:cross_cast_blacklist resolves to no Ars Zero glyph item "
                + "while ars_zero is loaded - the shipped ids no longer match the addon");
            return;
        }
        for (AbstractSpellPart part : tagged) {
            if (!ArsSpellIntegrity.isBlacklisted(part)) {
                helper.fail(part.getRegistryName() + " is in the tag but isBlacklisted() says no");
                return;
            }
            if (ArsSpellIntegrity.blacklistedGlyphIds(serialize(part)).isEmpty()) {
                helper.fail(part.getRegistryName() + " is tagged but its serialized payload passes "
                    + "blacklistedGlyphIds(CompoundTag) - a scroll carrying it would cast");
                return;
            }
            Spell live = new Spell().setRecipe(List.of(part));
            if (ArsSpellIntegrity.blacklistedGlyphIds(live).isEmpty()) {
                helper.fail(part.getRegistryName() + " is tagged but passes "
                    + "blacklistedGlyphIds(Spell) - the Spell Loom would export it");
                return;
            }
        }
        // And the control case: a stock Ars glyph must not be caught.
        AbstractSpellPart stock = GlyphRegistry.getSpellpartMap().values().stream()
            .filter(p -> p != null && p.getRegistryName() != null
                && CompatIds.ARS_NOUVEAU.equals(p.getRegistryName().getNamespace())
                && !ArsSpellIntegrity.isBlacklisted(p))
            .findFirst().orElse(null);
        if (stock == null) {
            helper.fail("every Ars Nouveau glyph is blacklisted - the tag file is wrong");
            return;
        }
        helper.succeed();
    }
}
