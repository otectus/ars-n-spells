package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.otectus.arsnspells.util.SchoolKeys;
import com.otectus.arsnspells.util.SchoolResolver;
import com.otectus.arsnspells.util.SpellAnalysis;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Audit ANS-005 against the live Ars Elemental 0.6.8.0 glyph whose two schools exposed the split:
 * Conjure Terrain declares conjuration and earth. Every consumer reads the same primary: affinity,
 * progression and primary scaling read {@code schoolKey()}; the mana and LP discounts read
 * {@code dominantSchool()}, which must be that key's built-in projection; and the membership set
 * that multi-school damage aggregation uses keeps both schools.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class SchoolAgreementGameTests {
    private SchoolAgreementGameTests() {}

    @GameTest(template = "platform")
    public static void arsElemental_conjureTerrain_everyConsumerReadsOnePrimary(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, "ars_elemental")) return;
        AbstractSpellPart part = GlyphRegistry.getSpellPart(new ResourceLocation("ars_elemental", "glyph_conjure_terrain"));
        helper.assertTrue(part != null, "ars_elemental:glyph_conjure_terrain is registered");
        SpellAnalysis.Result analysis = SpellAnalysis.analyze(new Spell(MethodSelf.INSTANCE, part));
        String primary = analysis.schoolKey();
        helper.assertTrue(SchoolKeys.builtin(primary).id().equals(analysis.dominantSchool()),
            "discounts (" + analysis.dominantSchool() + ") must read the projection of the credited primary " + primary);
        helper.assertTrue(SchoolResolver.resolve(part) == analysis.school(),
            "the resolver's single school is the analysis' primary: " + SchoolResolver.resolve(part) + " vs " + analysis.school());
        helper.assertTrue(primary.equals(SchoolResolver.resolveKeys(part).get(0)), "the primary is the first ordered membership");
        helper.assertTrue(analysis.allSchoolKeys().contains("irons_spellbooks:evocation") && analysis.allSchoolKeys().contains("irons_spellbooks:nature"),
            "multi-school aggregation still sees both memberships: " + analysis.allSchoolKeys());
        helper.assertTrue(analysis.schools().contains("evocation") && analysis.schools().contains("nature"),
            "and so does the built-in membership set: " + analysis.schools());
        SpellAnalysis.Result explicit = analysis.withExplicitSchool("irons_spellbooks:fire");
        helper.assertTrue("irons_spellbooks:fire".equals(explicit.schoolKey()) && "fire".equals(explicit.dominantSchool()),
            "an explicit school replaces every inferred membership");
        helper.succeed();
    }
}
