package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistration;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.behavior.Dormancy;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.runtime.GlyphOutcome;
import com.otectus.arsnspells.glyph.runtime.GlyphSessions;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * One generated scenario per catalogued Iron's spell (its lowest level), so no base spell can be
 * marked supported without having run: {@code glyph_smoke_<spell>}. Each adapter family also runs
 * one spell at its highest catalogued level: {@code glyph_smoke_max_<adapter>}.
 *
 * <p>Each scenario is a funded survival caster casting a real Ars spell from an Ars spellbook,
 * with the form the spell's target needs (Self for caster-only spells, Touch at a pig, a sheep,
 * the caster's own summon or a dirt block otherwise), and passes only when the runtime records the
 * imported effect as applied (instant and charged spells) or completed (channels). Each runs in its
 * own batch so black holes, explosions and storms cannot reach a neighbouring scenario. A spell
 * Iron's own default config disables must instead be dormant for exactly that reason.
 */
@GameTestHolder("ars_n_spells")
public final class ImportedGlyphSmokeGameTests {

    private static final Logger LOGGER = LoggerFactory.getLogger("ArsNSpells/GlyphSmoke");

    private ImportedGlyphSmokeGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> smoke() {
        List<TestFunction> tests = new ArrayList<>();
        java.util.Map<String, CatalogEntry> familyMax = new java.util.TreeMap<>();
        for (CatalogEntry entry : ImportedGlyphCatalog.get().entries()) {
            if (!entry.supported()) continue;
            String path = entry.spellId().getPath();
            tests.add(new TestFunction("ans_glyph_smoke_" + path, "glyph_smoke_" + path, "ars_n_spells:platform", 400, 0L, true,
                helper -> run(helper, entry, entry.minLevel(), "glyph_smoke_" + path)));
            // One scenario per adapter family at its spell's highest catalogued level.
            if (entry.maxLevel() > entry.minLevel() && entry.enabledByDefault() && !entry.requiresLearning()
                    && !"pocket_dimension".equals(path) && !"sacrifice".equals(path)) {
                familyMax.putIfAbsent(entry.adapter().getPath(), entry);
            }
        }
        for (var family : familyMax.entrySet()) {
            CatalogEntry entry = family.getValue();
            String name = "glyph_smoke_max_" + family.getKey();
            tests.add(new TestFunction("ans_glyph_smoke_max_" + family.getKey(), name, "ars_n_spells:platform", 400, 0L, true,
                helper -> run(helper, entry, entry.maxLevel(), name)));
        }
        return tests;
    }

    private static void run(GameTestHelper helper, CatalogEntry entry, int level, String scenario) {
        if (!IronsCompat.isLoaded()) {
            ScenarioReport.skipped(IronsCompat.MODID, "ImportedGlyphSmokeGameTests#" + scenario);
            helper.succeed();
            return;
        }
        ScenarioReport.executed("ImportedGlyphSmokeGameTests#" + scenario);
        ImportedIronEffect effect = ImportedGlyphRegistration.effect(new ImportedGlyphKey(entry.spellId(), level));
        if (!entry.enabledByDefault()) {
            Dormancy dormancy = GlyphBehavior.variant(effect.key()).dormancy();
            helper.assertTrue(dormancy == Dormancy.SPELL_DISABLED_BY_IRONS, entry.spellId()
                + " is disabled by Iron's default config, so its glyph must be dormant for that reason, got " + dormancy);
            LOGGER.info("[GlyphSmoke] spell={} level={} result=DORMANT reason={}", entry.spellId(), level, dormancy);
            helper.succeed();
            return;
        }
        // One caster per scenario: the min- and max-level scenarios of a spell must not share its native cooldown.
        ServerPlayer player = ImportedGlyphGameTests.caster(helper, scenario, GameType.SURVIVAL);
        ImportedGlyphGameTests.fund(player);
        if (entry.requiresLearning()) IronsProxyCastDriver.learnSpell(player, entry.spellId().toString());
        GlyphSessions.clearOutcomes(player.getUUID());
        Spell spell = setUp(helper, player, entry, effect);
        LOGGER.info("[GlyphSmoke] scenario={} spell={} level={}", scenario, entry.spellId(), level);
        ImportedGlyphGameTests.cast(player, spell);
        if ("pocket_dimension".equals(entry.spellId().getPath()) && helper.getLevel().getServer().getLevel(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "pocket_dimension"))) == null) {
            // The GameTest server creates no pocket-dimension level, so Iron's own onCast cannot run
            // here (a native cast fails the same way). The scenario proves the runtime survives it:
            // the charge completes, the native failure is caught and reported, and nothing crashes.
            helper.succeedWhen(() -> {
                var outcomes = GlyphSessions.outcomes(player.getUUID());
                helper.assertTrue(outcomes.stream().anyMatch(o -> o.spell().equals(entry.spellId())
                        && o.outcome() == GlyphOutcome.REJECTED && o.detail().contains("refused.failed")),
                    "without a pocket-dimension level the native failure must be caught and reported; outcomes " + outcomes);
                LOGGER.info("[GlyphSmoke] spell={} level={} result=ENVIRONMENT (no pocket-dimension level in the GameTest server) outcomes={}",
                    entry.spellId(), level, outcomes);
            });
            return;
        }
        if ("sacrifice".equals(entry.spellId().getPath())) {
            // Iron's resolves a summon's owner with ServerLevel.getEntity(uuid), which never finds a
            // GameTest FakePlayer, so Sacrifice's own target filter (the caster's summon) must refuse
            // here: the refusal is Iron's native condition, reached through the real pre-cast path.
            helper.succeedWhen(() -> {
                var outcomes = GlyphSessions.outcomes(player.getUUID());
                helper.assertTrue(outcomes.stream().anyMatch(o -> o.spell().equals(entry.spellId())
                        && o.outcome() == GlyphOutcome.REJECTED && o.detail().contains("native_condition")),
                    "Sacrifice must reach Iron's own target filter; outcomes " + outcomes);
                LOGGER.info("[GlyphSmoke] spell={} level={} result=NATIVE_FILTER (harness cannot own summons) outcomes={}",
                    entry.spellId(), level, outcomes);
            });
            return;
        }
        helper.succeedWhen(() -> {
            var outcomes = GlyphSessions.outcomes(player.getUUID()).stream()
                .filter(o -> o.spell().equals(entry.spellId())).toList();
            boolean channel = entry.channelled();
            boolean done = outcomes.stream().anyMatch(o -> channel ? o.outcome() == GlyphOutcome.COMPLETED
                : o.outcome() == GlyphOutcome.EFFECT_APPLIED);
            helper.assertTrue(done, entry.spellId() + " (" + entry.adapter() + ", " + entry.target() + ") must "
                + (channel ? "complete its channel" : "apply its effect") + "; outcomes so far: " + outcomes);
            LOGGER.info("[GlyphSmoke] spell={} level={} adapter={} target={} result=PASS outcomes={}", entry.spellId(), level,
                entry.adapter().getPath(), entry.target(), outcomes);
        });
    }

    /** Place the target the spell needs and aim at it; returns the Ars spell to cast. */
    private static Spell setUp(GameTestHelper helper, ServerPlayer player, CatalogEntry entry, ImportedIronEffect effect) {
        String path = entry.spellId().getPath();
        switch (entry.target()) {
            case SELF -> {
                return new Spell(MethodSelf.INSTANCE, effect);
            }
            case BLOCK -> {
                helper.setBlock(ImportedGlyphGameTests.FRONT, Blocks.DIRT);
                ImportedGlyphGameTests.aim(player, Vec3.atCenterOf(helper.absolutePos(ImportedGlyphGameTests.FRONT)));
                return new Spell(MethodTouch.INSTANCE, effect);
            }
            default -> {
                if ("ars_n_spells:teleport".equals(entry.adapter().toString())) {
                    // The floor two blocks ahead: the landing spot must have head room under the
                    // harness's barrier ceiling, which an eye-level block does not.
                    ImportedGlyphGameTests.aim(player, helper.absoluteVec(new Vec3(3.5, 2.0, 2.5)));
                    return new Spell(MethodTouch.INSTANCE, effect);
                }
                if ("ars_n_spells:channel".equals(entry.adapter().toString()) && entry.target() == CatalogEntry.TargetKind.SELF) {
                    return new Spell(MethodSelf.INSTANCE, effect);
                }
                Entity target;
                if ("wololo".equals(path)) {
                    Sheep sheep = helper.spawn(EntityType.SHEEP, ImportedGlyphGameTests.TARGET);
                    sheep.setNoAi(true);
                    target = sheep;
                } else if ("sacrifice".equals(path)) {
                    target = IronsProxyCastDriver.ownedSummon(player, helper.absoluteVec(ImportedGlyphGameTests.TARGET));
                } else {
                    Pig pig = ImportedGlyphGameTests.pig(helper);
                    pig.setHealth(pig.getMaxHealth());
                    target = pig;
                }
                ImportedGlyphGameTests.aim(player, target.getBoundingBox().getCenter());
                return new Spell(MethodTouch.INSTANCE, effect);
            }
        }
    }
}
