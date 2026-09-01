package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.util.ArsSpellIntegrity;
import com.otectus.arsnspells.util.SchoolResolver;
import com.otectus.arsnspells.util.SpellSchoolId;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Addon compatibility, exercised against real addon glyphs rather than synthetic ones.
 *
 * <p>Every test here self-skips when its addon is absent, so the default GameTest run is
 * unaffected. Enable with the opt-in profile:
 *
 * <pre>
 *   ./gradlew runGameTestServer -PwithArsElemental
 * </pre>
 *
 * <p>The point is to falsify the "total addon compatibility" claim or earn it. A glyph from an
 * addon has to survive the full data path - serialize, integrity-check, deserialize - and its
 * school has to come from the addon's declared metadata rather than from a lucky substring
 * match on its registry path.
 *
 * <p>The 1.20.1 line also carried a Too Many Glyphs profile. That mod has no 1.21.1 release, so
 * per the standing rule on mods without a 1.21.1 build there is no counterpart here.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class AddonCompatGameTests {

    private static final String ARS_ELEMENTAL = "ars_elemental";

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

    /** Serialize one glyph the way an inscribed item stores it. */
    private static CompoundTag serialize(AbstractSpellPart glyph) {
        Spell spell = new Spell().setRecipe(List.of(glyph));
        Tag encoded = Spell.CODEC.codec().encodeStart(NbtOps.INSTANCE, spell).result().orElse(null);
        return encoded instanceof CompoundTag tag ? tag : new CompoundTag();
    }

    @GameTest(template = "platform")
    public static void arsElemental_glyphsRoundTrip(GameTestHelper helper) {
        if (!loaded(ARS_ELEMENTAL)) {
            helper.succeed();
            return;
        }
        List<AbstractSpellPart> glyphs = glyphsOf(ARS_ELEMENTAL);
        if (glyphs.isEmpty()) {
            helper.fail(ARS_ELEMENTAL + " is loaded but registers no glyphs; the profile is "
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

    @GameTest(template = "platform")
    public static void arsElemental_schoolsResolveFromDeclaredMetadata(GameTestHelper helper) {
        if (!loaded(ARS_ELEMENTAL)) {
            helper.succeed();
            return;
        }
        List<AbstractSpellPart> glyphs = glyphsOf(ARS_ELEMENTAL);
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
                SchoolResolver.resolve(ARS_ELEMENTAL + ":zzz_unmatchable_path", schoolIds);
            if (!viaMetadata.isGeneric()) {
                resolved++;
            }
        }

        if (declared == 0) {
            helper.fail(ARS_ELEMENTAL + " declares no spell schools on any glyph - either the "
                + "addon changed, or the profile loaded the wrong artifact");
            return;
        }
        if (resolved == 0) {
            helper.fail(ARS_ELEMENTAL + " declares schools on " + declared + " glyph(s) but none "
                + "translate to an ANS school. The Ars-school translation table has drifted from "
                + "the addon's vocabulary.");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void arsElemental_everyGlyphResolvesWithoutThrowing(GameTestHelper helper) {
        if (!loaded(ARS_ELEMENTAL)) {
            helper.succeed();
            return;
        }
        for (AbstractSpellPart glyph : glyphsOf(ARS_ELEMENTAL)) {
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
}
