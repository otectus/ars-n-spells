package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.capability.IPlayerCap;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.otectus.arsnspells.block.GlyphPressBlockEntity;
import com.otectus.arsnspells.casting.PaymentTimeline;
import com.otectus.arsnspells.glyph.GlyphAvailability;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistrar;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.recipe.GlyphPressPlan;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;

/**
 * 3.4.1 review CATALOG-01: Wind's Spellbooks, a real Iron's addon with its own school, as imported
 * glyphs through the catalog package Ars 'n' Spells ships ({@code catalogs/wind_spellbooks/}). Every
 * castable spell is cast through an Ars spell and its native effect observed in the world (an effect on
 * the caster, the addon's own entity, damage to the target, the caster launched); Iron Slash is
 * catalogued dormant and refuses unpaid; a Wind scroll presses into its exact glyph, which teaches,
 * round-trips and keeps Iron's live limits. Runs in the {@code -PwithIrons316RuntimeGameTests
 * -PwithWindSpellbooks -PwithCatalogPackages=wind_spellbooks} profile and skips without the addon; with
 * the addon but without the package it fails, naming the package.
 *
 * <p>Only registry ids of the addon are used, never its classes, so this class links without it.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class WindSpellbooksGameTests {
    static final String MODID = "wind_spellbooks";
    private static final String BATCH = "ans_wind_spellbooks";

    private WindSpellbooksGameTests() {}

    private static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    /** Skips without the addon; fails, naming the package, when the addon is loaded but its catalog is not. */
    private static boolean skip(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, MODID)) return true;
        if (ImportedGlyphCatalog.get().levelsOf(id("wind_blade")).isEmpty()) {
            helper.fail("Wind's Spellbooks is loaded but its glyph catalog is not: install catalogs/wind_spellbooks "
                + "(-PwithCatalogPackages=wind_spellbooks), or the ans_catalog_wind_spellbooks jar");
            return true;
        }
        return false;
    }

    private static ImportedIronEffect glyph(String spell, int level) {
        return ImportedGlyphRegistrar.part(new ImportedGlyphKey(id(spell), level))
            .orElseThrow(() -> new IllegalStateException("no imported glyph for " + spell + " " + level));
    }

    private static ServerPlayer caster(GameTestHelper helper, String name) {
        return CastPolicyGameTests.caster(helper, name);
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, at);
        var maxHealth = pig.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(200.0);
        pig.setHealth(200.0f);
        return pig;
    }

    private static int entities(GameTestHelper helper, String type, AABB box) {
        ResourceLocation wanted = id(type);
        return helper.getLevel().getEntities((Entity) null, box, e -> wanted.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()))).size();
    }

    private static MobEffect effect(String path) {
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(id(path));
        if (effect == null) throw new IllegalStateException("Wind's Spellbooks registers no effect " + path);
        return effect;
    }

    // ---- The package ---------------------------------------------------------------------------

    @GameTest(template = "platform", batch = BATCH)
    public static void windLoaded_everyWindSpellIsCataloguedWithItsVerdict(GameTestHelper helper) {
        if (skip(helper)) return;
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.get();
        Map<String, Boolean> expected = Map.of("aeropic", true, "tailwind", true, "almighty_push", true, "wind_jump", true,
            "wind_blade", true, "tornado", false, "iron_slash", false);
        for (Map.Entry<String, Boolean> spell : expected.entrySet()) {
            List<ImportedGlyphDefinition> levels = catalog.levelsOf(id(spell.getKey()));
            helper.assertTrue(levels.size() == 10 && levels.get(0).level() == 1, spell.getKey() + " registers levels I-X: " + levels.size());
            helper.assertTrue(levels.get(0).executable() == spell.getValue(), spell.getKey() + " executable " + spell.getValue());
            helper.assertTrue(levels.get(0).school().equals(id("wind")), spell.getKey() + " is catalogued in the wind school");
        }
        helper.assertTrue(GlyphBehavior.facts(catalog.levelsOf(id("tornado")).get(0)).school().equals(id("wind")),
            "the live school comes from Iron's registry: the addon's own wind school");
        helper.assertTrue(catalog.provenance(id("wind_blade")).map(p -> p.catalog().equals(MODID)).orElse(false),
            "the identities come from the wind_spellbooks package: " + catalog.provenance(id("wind_blade")));
        helper.succeed();
    }

    // ---- Casting: each castable spell's own effect ------------------------------------------------

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 100)
    public static void windLoaded_aeropicAndTailwindBuffTheCaster(GameTestHelper helper) {
        if (skip(helper)) return;
        ServerPlayer aeropic = caster(helper, "wind_aeropic");
        ImportedGlyphGameTests.cast(helper, aeropic, new Spell(MethodSelf.INSTANCE, glyph("aeropic", 1)));
        ServerPlayer tailwind = caster(helper, "wind_tailwind");
        ImportedGlyphGameTests.cast(helper, tailwind, new Spell(MethodSelf.INSTANCE, glyph("tailwind", 1)));
        helper.succeedWhen(() -> {
            helper.assertTrue(aeropic.hasEffect(effect("aeropic")), "Aeropic gives the caster its effect");
            helper.assertTrue(tailwind.hasEffect(effect("tailwind")), "Tailwind gives the caster its effect");
            helper.assertTrue(debited(aeropic) && debited(tailwind), "each cast was paid: " + PaymentTimeline.entries(aeropic.getUUID()));
        });
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 100)
    public static void windLoaded_windBladeHitsTheTouchedTarget(GameTestHelper helper) {
        if (skip(helper)) return;
        ServerPlayer player = caster(helper, "wind_blade");
        Pig pig = pig(helper, new Vec3(2.5, 2.0, 3.5));
        TouchRegressionGameTests.aimAt(player, pig.getBoundingBox().getCenter());
        ImportedGlyphGameTests.cast(helper, player, new Spell(MethodTouch.INSTANCE, glyph("wind_blade", 1)));
        helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() < 200.0f, "the Wind Blade, delivered to the touched pig, wounds it"));
    }

    /**
     * Wind's Spellbooks 1.20.1-1.0.0's Tornado cannot run on Iron's 1.20.1-3.16.3 even natively (its entity
     * reads a field that Iron's build lacks), so its package catalogues it dormant: a cast refuses, unpaid,
     * and none of the addon's failing code runs.
     */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 100)
    public static void windLoaded_tornadoIsDormantOnThisIronsBuild(GameTestHelper helper) {
        if (skip(helper)) return;
        GlyphAvailability.Status status = GlyphAvailability.status(ImportedGlyphCatalog.get().levelsOf(id("tornado")).get(0));
        helper.assertTrue(status.reason() == GlyphAvailability.Reason.UNSUPPORTED, "Tornado is catalogued dormant: " + status);
        ServerPlayer player = caster(helper, "wind_tornado");
        Pig pig = pig(helper, new Vec3(2.5, 2.0, 3.5));
        TouchRegressionGameTests.aimAt(player, pig.getBoundingBox().getCenter());
        ImportedGlyphGameTests.cast(helper, player, new Spell(MethodTouch.INSTANCE, glyph("tornado", 1)));
        AABB atPig = pig.getBoundingBox().inflate(3.0);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(entities(helper, "tornado", atPig) == 0, "no Tornado entity was built");
            CastPolicyGameTests.assertNetImportedDebit(helper, player, 0.0);
            helper.succeed();
        });
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 100)
    public static void windLoaded_almightyPushSpawnsOnTheCaster(GameTestHelper helper) {
        if (skip(helper)) return;
        ServerPlayer player = caster(helper, "wind_push");
        ImportedGlyphGameTests.cast(helper, player, new Spell(MethodSelf.INSTANCE, glyph("almighty_push", 1)));
        AABB around = player.getBoundingBox().inflate(3.0);
        helper.succeedWhen(() -> helper.assertTrue(entities(helper, "almighty_push", around) > 0, "the Almighty Push forms on the caster"));
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 100)
    public static void windLoaded_windJumpLaunchesTheCaster(GameTestHelper helper) {
        if (skip(helper)) return;
        ServerPlayer player = caster(helper, "wind_jump");
        ImportedGlyphGameTests.cast(helper, player, new Spell(MethodSelf.INSTANCE, glyph("wind_jump", 1)));
        helper.succeedWhen(() -> helper.assertTrue(player.hasEffect(MobEffects.SLOW_FALLING), "Wind Jump gives the caster slow falling"));
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 100)
    public static void windLoaded_ironSlashIsDormantAndRefusesUnpaid(GameTestHelper helper) {
        if (skip(helper)) return;
        ImportedGlyphDefinition slash = ImportedGlyphCatalog.get().levelsOf(id("iron_slash")).get(0);
        GlyphAvailability.Status status = GlyphAvailability.status(slash);
        helper.assertTrue(status.reason() == GlyphAvailability.Reason.UNSUPPORTED, "Iron Slash is catalogued dormant: " + status);
        ServerPlayer player = caster(helper, "wind_slash");
        Vec3 before = player.position();
        ImportedGlyphGameTests.cast(helper, player, new Spell(MethodSelf.INSTANCE, glyph("iron_slash", 1)));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(player.position().distanceTo(before) < 0.5, "no dash happened");
            CastPolicyGameTests.assertNetImportedDebit(helper, player, 0.0);
            helper.succeed();
        });
    }

    // ---- Press, learn, save, augment limits --------------------------------------------------------

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 400)
    public static void windLoaded_aWindScrollPressesIntoItsExactGlyph(GameTestHelper helper) {
        if (skip(helper)) return;
        GlyphPressBlockEntity press = GlyphPressGameTests.place(helper);
        ServerPlayer player = GlyphPressGameTests.player(helper, "wind_press");
        GlyphPressGameTests.load(helper, press, GlyphPressGameTests.scroll("wind_spellbooks:wind_blade", 3),
            GlyphPressGameTests.plates(1), GlyphPressGameTests.gems(4));
        press.addSource(4000);
        GlyphPressPlan plan = press.plan(player);
        helper.assertTrue(plan.ok() && GlyphPressGameTests.isGlyph(plan.output(), "wind_spellbooks:wind_blade", 3),
            "a Wind Blade III scroll plans Wind Blade III: " + plan.reason() + " " + plan.output());
        helper.assertTrue(GlyphPressBlockEntity.STARTED.equals(press.start(player)), "the job starts");
        helper.succeedWhen(() -> {
            helper.assertTrue(!press.working(), "still engraving: " + press.describe());
            helper.assertTrue(GlyphPressGameTests.isGlyph(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT),
                "wind_spellbooks:wind_blade", 3), "exactly Wind Blade III");
        });
    }

    @GameTest(template = "platform", batch = BATCH)
    public static void windLoaded_glyphsTeachRoundTripAndKeepIronsLimits(GameTestHelper helper) {
        if (skip(helper)) return;
        ServerPlayer player = caster(helper, "wind_learn");
        ImportedIronEffect blade = glyph("wind_blade", 2);
        IPlayerCap cap = CapabilityRegistry.getPlayerDataCap(player).orElseThrow(IllegalStateException::new);
        helper.assertTrue(!cap.knowsGlyph(blade), "a fresh player does not know Wind Blade II");
        ItemStack item = new ItemStack(blade.getGlyph());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, item);
        item.use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(cap.knowsGlyph(blade), "using the glyph teaches it");
        Spell spell = new Spell(MethodTouch.INSTANCE, blade, AugmentAmplify.INSTANCE);
        Spell back = Spell.fromTag(spell.serialize());
        helper.assertTrue(back.recipe.size() == 3 && back.recipe.get(1) == blade, "the recipe round-trips: " + back.recipe);
        ImportedIronEffect tailwind = glyph("tailwind", 1);
        helper.assertTrue(tailwind.getAugmentLimit(AugmentAmplify.INSTANCE.getRegistryName()) == 2,
            "Amplify lifts Tailwind I to Iron's live maximum III and no further");
        helper.assertTrue(GlyphAvailability.status(ImportedGlyphCatalog.get().byKey(new ImportedGlyphKey(id("tailwind"), 4)).orElseThrow())
            .reason() == GlyphAvailability.Reason.LEVEL_UNSUPPORTED, "Tailwind IV is registered but above Iron's live maximum");
        helper.succeed();
    }

    private static boolean debited(ServerPlayer player) {
        return PaymentTimeline.entries(player.getUUID()).stream()
            .anyMatch(e -> e.kind() == PaymentTimeline.Kind.COMMIT && e.label().startsWith("glyph:"));
    }
}
