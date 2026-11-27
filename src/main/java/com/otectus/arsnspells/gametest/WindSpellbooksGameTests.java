package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.capability.IPlayerCap;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.otectus.arsnspells.block.GlyphPressBlockEntity;
import com.otectus.arsnspells.casting.PaymentTimeline;
import com.otectus.arsnspells.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistration;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.behavior.Dormancy;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import com.otectus.arsnspells.network.GlyphPressActionPayload;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * 3.4.1 review CATALOG-01 on NeoForge: Wind's Spellbooks, a real Iron's addon with its own school, as
 * imported glyphs through the catalog package Ars 'n' Spells ships ({@code catalogs/wind_spellbooks/}).
 * The same scenarios as the Forge build's class of the same name: each castable spell's native effect
 * observed in the world, Iron Slash dormant and unpaid, a Wind scroll pressed into its exact glyph,
 * learning, a recipe round trip and Iron's live limits. Runs in the {@code -PwithIronsRuntimeGameTests
 * -PwithWindSpellbooks -PwithCatalogPackages=wind_spellbooks} profile; skips without the addon, and with
 * the addon but not the package fails, naming the package. Only the addon's registry ids are used.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class WindSpellbooksGameTests {
    static final String MODID = "wind_spellbooks";
    private static final String BATCH = "ans_wind_spellbooks";

    private WindSpellbooksGameTests() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static boolean skip(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, MODID)) return true;
        if (ImportedGlyphCatalog.get().entry(id("wind_blade")) == null) {
            helper.fail("Wind's Spellbooks is loaded but its glyph catalog is not: install catalogs/wind_spellbooks "
                + "(-PwithCatalogPackages=wind_spellbooks), or the ans_catalog_wind_spellbooks jar");
            return true;
        }
        return false;
    }

    private static ImportedIronEffect glyph(String spell, int level) {
        return ImportedGlyphRegistration.effect(new ImportedGlyphKey(id(spell), level));
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        return CastReviewGameTests.sturdyPig(helper, at);
    }

    private static int entities(GameTestHelper helper, String type, AABB box) {
        ResourceLocation wanted = id(type);
        return helper.getLevel().getEntities((Entity) null, box, e -> wanted.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()))).size();
    }

    private static Holder<MobEffect> effect(String path) {
        return BuiltInRegistries.MOB_EFFECT.getHolder(ResourceKey.create(Registries.MOB_EFFECT, id(path)))
            .orElseThrow(() -> new IllegalStateException("Wind's Spellbooks registers no effect " + path));
    }

    private static boolean paid(ServerPlayer player) {
        return PaymentTimeline.entries(player.getUUID()).stream()
            .anyMatch(e -> e.kind() == PaymentTimeline.Kind.COMMIT && e.label().startsWith("glyph:"));
    }

    @GameTest(template = "platform", batch = BATCH)
    public static void windLoaded_everyWindSpellIsCataloguedWithItsVerdict(GameTestHelper helper) {
        if (skip(helper)) return;
        Map<String, Boolean> expected = Map.of("aeropic", true, "tailwind", true, "almighty_push", true, "wind_jump", true,
            "wind_blade", true, "tornado", false, "iron_slash", false);
        for (Map.Entry<String, Boolean> spell : expected.entrySet()) {
            CatalogEntry entry = ImportedGlyphCatalog.get().entry(id(spell.getKey()));
            helper.assertTrue(entry != null && entry.minLevel() == 1 && entry.registeredMax() == 10, spell.getKey() + " registers levels I-X");
            helper.assertTrue(entry.executable() == spell.getValue(), spell.getKey() + " executable " + spell.getValue());
            helper.assertTrue(entry.school().equals(id("wind")), spell.getKey() + " is catalogued in the wind school");
            helper.assertTrue(MODID.equals(entry.catalog()), spell.getKey() + " comes from the wind_spellbooks package: " + entry.catalog());
        }
        helper.assertTrue(GlyphBehavior.current().spell(id("tornado")).school().equals(id("wind")),
            "the live school comes from Iron's registry: the addon's own wind school");
        helper.succeed();
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 100)
    public static void windLoaded_aeropicAndTailwindBuffTheCaster(GameTestHelper helper) {
        if (skip(helper)) return;
        ServerPlayer aeropic = CastReviewGameTests.caster(helper, "wind_aeropic");
        ImportedGlyphGameTests.cast(aeropic, ImportedGlyphGameTests.spell(MethodSelf.INSTANCE, glyph("aeropic", 1)));
        ServerPlayer tailwind = CastReviewGameTests.caster(helper, "wind_tailwind");
        ImportedGlyphGameTests.cast(tailwind, ImportedGlyphGameTests.spell(MethodSelf.INSTANCE, glyph("tailwind", 1)));
        helper.succeedWhen(() -> {
            helper.assertTrue(aeropic.hasEffect(effect("aeropic")), "Aeropic gives the caster its effect");
            helper.assertTrue(tailwind.hasEffect(effect("tailwind")), "Tailwind gives the caster its effect");
        });
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 100)
    public static void windLoaded_windBladeHitsTheTouchedTarget(GameTestHelper helper) {
        if (skip(helper)) return;
        ServerPlayer player = CastReviewGameTests.caster(helper, "wind_blade");
        Pig pig = pig(helper, new Vec3(3.5, 2.0, 2.5));
        float health = pig.getHealth();
        ImportedGlyphGameTests.aim(player, pig.getBoundingBox().getCenter());
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodTouch.INSTANCE, glyph("wind_blade", 1)));
        helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() < health, "the Wind Blade, delivered to the touched pig, wounds it"));
    }

    /**
     * Wind's Spellbooks 1.0.1's Tornado cannot run on Iron's 1.21.1-3.16.3 even natively (its entity reads
     * a field this Iron's build lacks), so its package catalogues it dormant: a cast refuses, unpaid, and
     * nothing of the addon's broken code runs.
     */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 100)
    public static void windLoaded_tornadoIsDormantOnThisIronsBuild(GameTestHelper helper) {
        if (skip(helper)) return;
        Dormancy dormancy = GlyphBehavior.current().variant(new ImportedGlyphKey(id("tornado"), 1)).dormancy();
        helper.assertTrue(dormancy == Dormancy.UNSUPPORTED, "Tornado is catalogued dormant: " + dormancy);
        ServerPlayer player = CastReviewGameTests.caster(helper, "wind_tornado");
        Pig pig = pig(helper, new Vec3(3.5, 2.0, 2.5));
        ImportedGlyphGameTests.aim(player, pig.getBoundingBox().getCenter());
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodTouch.INSTANCE, glyph("tornado", 1)));
        AABB atPig = pig.getBoundingBox().inflate(3.0);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(entities(helper, "tornado", atPig) == 0, "no Tornado entity was built");
            CastReviewGameTests.assertNetImportedDebit(helper, player, 0.0);
            helper.succeed();
        });
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 100)
    public static void windLoaded_almightyPushSpawnsOnTheCaster(GameTestHelper helper) {
        if (skip(helper)) return;
        ServerPlayer player = CastReviewGameTests.caster(helper, "wind_push");
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodSelf.INSTANCE, glyph("almighty_push", 1)));
        AABB around = player.getBoundingBox().inflate(3.0);
        helper.succeedWhen(() -> helper.assertTrue(entities(helper, "almighty_push", around) > 0, "the Almighty Push forms on the caster"));
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 100)
    public static void windLoaded_windJumpLaunchesTheCaster(GameTestHelper helper) {
        if (skip(helper)) return;
        ServerPlayer player = CastReviewGameTests.caster(helper, "wind_jump");
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodSelf.INSTANCE, glyph("wind_jump", 1)));
        helper.succeedWhen(() -> helper.assertTrue(player.hasEffect(MobEffects.SLOW_FALLING), "Wind Jump gives the caster slow falling"));
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 100)
    public static void windLoaded_ironSlashIsDormantAndRefusesUnpaid(GameTestHelper helper) {
        if (skip(helper)) return;
        Dormancy dormancy = GlyphBehavior.current().variant(new ImportedGlyphKey(id("iron_slash"), 1)).dormancy();
        helper.assertTrue(dormancy == Dormancy.UNSUPPORTED, "Iron Slash is catalogued dormant: " + dormancy);
        ServerPlayer player = CastReviewGameTests.caster(helper, "wind_slash");
        Vec3 before = player.position();
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodSelf.INSTANCE, glyph("iron_slash", 1)));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(player.position().distanceTo(before) < 0.5, "no dash happened");
            CastReviewGameTests.assertNetImportedDebit(helper, player, 0.0);
            helper.succeed();
        });
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 400)
    public static void windLoaded_aWindScrollPressesIntoItsExactGlyph(GameTestHelper helper) {
        if (skip(helper)) return;
        GlyphPressBlockEntity press = GlyphPressGameTests.press(helper);
        ServerPlayer player = GlyphPressGameTests.player(helper, "wind_press");
        ImportedGlyphKey key = new ImportedGlyphKey(id("wind_blade"), 3);
        GlyphPressGameTests.load(press, IronGlyphBridges.get().scrollStack(id("wind_blade"), 3), 1,
            GlyphBehavior.current().variant(key).pressGems());
        GlyphPressGameTests.fillSource(press, 4000);
        String outcome = GlyphPressGameTests.request(player, GlyphPressGameTests.open(player, press), GlyphPressActionPayload.ACTION_INSCRIBE);
        helper.assertTrue(press.job() != null, "the Wind Blade III job starts: " + outcome);
        helper.succeedWhen(() -> {
            ItemStack out = press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT);
            helper.assertTrue(out.is(ImportedGlyphRegistration.effect(key).getGlyph()), "exactly Wind Blade III: " + out);
        });
    }

    @GameTest(template = "platform", batch = BATCH)
    public static void windLoaded_glyphsTeachRoundTripAndKeepIronsLimits(GameTestHelper helper) {
        if (skip(helper)) return;
        ServerPlayer player = GlyphPressGameTests.player(helper, "wind_learn");
        ImportedIronEffect blade = glyph("wind_blade", 2);
        IPlayerCap cap = CapabilityRegistry.getPlayerDataCap(player);
        helper.assertTrue(!cap.knowsGlyph(blade), "a fresh player does not know Wind Blade II");
        ItemStack item = new ItemStack(blade.getGlyph());
        player.setItemInHand(InteractionHand.MAIN_HAND, item);
        item.use(player.level(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(cap.knowsGlyph(blade), "using the glyph teaches it");
        Spell spell = ImportedGlyphGameTests.spell(MethodTouch.INSTANCE, blade, AugmentAmplify.INSTANCE);
        Tag encoded = Spell.CODEC.codec().encodeStart(NbtOps.INSTANCE, spell).result().orElseThrow();
        Spell back = Spell.CODEC.codec().parse(NbtOps.INSTANCE, encoded).result().orElseThrow();
        helper.assertTrue(back.unsafeList().size() == 3 && back.unsafeList().get(1) == blade, "the recipe round-trips: " + back.unsafeList());
        helper.assertTrue(glyph("tailwind", 1).getAugmentLimit(AugmentAmplify.INSTANCE.getRegistryName()) == 2,
            "Amplify lifts Tailwind I to Iron's live maximum III and no further");
        helper.assertTrue(GlyphBehavior.current().variant(new ImportedGlyphKey(id("tailwind"), 4)).dormancy() == Dormancy.LEVEL_ABOVE_SERVER_MAX,
            "Tailwind IV is registered but above Iron's live maximum");
        helper.assertTrue(!paid(player), "nothing here was paid");
        helper.succeed();
    }
}
