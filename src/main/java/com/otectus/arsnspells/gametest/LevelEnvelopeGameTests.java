package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.capability.IPlayerCap;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.otectus.arsnspells.block.GlyphPressBlockEntity;
import com.otectus.arsnspells.casting.PaymentTimeline;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.glyph.GlyphAvailability;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistrar;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.recipe.GlyphPressPlan;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 3.4.1 review CATALOG-01: the documented path to Iron's maximum levels above X. Ars 'n' Spells ships the
 * {@code irons_envelope_15} catalog package ({@code catalogs/irons_envelope_15/}): installed on the
 * server and every client and followed by a restart, it reserves identities XI-XV for every Iron's spell
 * of the shipped catalog, under the shipped adapter contracts and with levels I-X unchanged. The live
 * range still follows Iron's maximum: a maximum of 1, 5, 10 or 15 makes exactly the levels above it
 * dormant, a per-spell maximum applies to that spell only, a maximum above XV leaves XVI and up
 * without identities (Amplify may still raise a cast to Iron's maximum), and a lowered maximum makes
 * the higher identities dormant without touching them. Each scenario changes Iron's maximum, so each
 * owns its batch and restores it there. Runs in the {@code -PwithCatalogPackages=irons_envelope_15}
 * profile with Iron's loaded; skips without the package.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class LevelEnvelopeGameTests {
    static final String PACKAGE = "ans_catalog_irons_envelope_15";
    private static final String FIREBOLT = "irons_spellbooks:firebolt";
    private static final String HEAL = "irons_spellbooks:heal";
    private static volatile int fireboltBefore = -1;
    private static volatile int healBefore = -1;

    private LevelEnvelopeGameTests() {}

    private static boolean skip(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return true;
        return OptionalModGate.skipIfAbsent(helper, PACKAGE);
    }

    private static ImportedGlyphDefinition definition(String spell, int level) {
        return ImportedGlyphCatalog.get().byKey(new ImportedGlyphKey(new ResourceLocation(spell), level)).orElseThrow();
    }

    private static ImportedIronEffect glyph(String spell, int level) {
        return ImportedGlyphRegistrar.part(new ImportedGlyphKey(new ResourceLocation(spell), level)).orElseThrow();
    }

    private static GlyphAvailability.Reason reason(String spell, int level) {
        return GlyphAvailability.status(definition(spell, level)).reason();
    }

    private static void setMax(String spell, int max) {
        int previous = Glyph341IronsSupport.setMaxLevel(spell, max);
        if (FIREBOLT.equals(spell) && fireboltBefore < 0) fireboltBefore = previous;
        if (HEAL.equals(spell) && healBefore < 0) healBefore = previous;
        GlyphBehavior.rebuild();
    }

    private static void restore() {
        if (!IronsCompat.isLoaded()) return;
        if (fireboltBefore > 0) Glyph341IronsSupport.setMaxLevel(FIREBOLT, fireboltBefore);
        if (healBefore > 0) Glyph341IronsSupport.setMaxLevel(HEAL, healBefore);
        fireboltBefore = healBefore = -1;
        GlyphBehavior.rebuild();
    }

    @AfterBatch(batch = "ans_envelope_maxima") public static void afterMaxima(ServerLevel level) { restore(); }
    @AfterBatch(batch = "ans_envelope_cast") public static void afterCast(ServerLevel level) { restore(); }
    @AfterBatch(batch = "ans_envelope_beyond") public static void afterBeyond(ServerLevel level) { restore(); }
    @AfterBatch(batch = "ans_envelope_lowered") public static void afterLowered(ServerLevel level) { restore(); }

    @GameTest(template = "platform", batch = "ans_envelope_identities")
    public static void envelope15_reservesXIToXVUnderTheShippedContract(GameTestHelper helper) {
        if (skip(helper)) return;
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.get();
        for (ResourceLocation spell : catalog.spells()) {
            if (!"irons_spellbooks".equals(spell.getNamespace())) continue;
            helper.assertTrue(catalog.minLevel(spell) == 1 && catalog.registeredMax(spell) == 15, spell + " registers I-XV: "
                + catalog.minLevel(spell) + "-" + catalog.registeredMax(spell));
        }
        List<ImportedGlyphDefinition> levels = catalog.levelsOf(new ResourceLocation(FIREBOLT));
        ImportedGlyphDefinition first = levels.get(0);
        helper.assertTrue(first.executable() && first.family().adapterId().equals("ars_n_spells:projectile"),
            "Firebolt keeps the shipped projectile contract: " + first.family().adapterId() + " v" + first.adapterVersion());
        helper.assertTrue(first.source().startsWith("ars_n_spells" + ImportedGlyphCatalog.RESOURCE) && first.source().contains(PACKAGE),
            "the shipped catalog owns the spell and the package extends it: " + first.source());
        helper.assertTrue(!levels.get(9).reserved() && levels.get(10).reserved() && levels.get(14).reserved()
                && "LEGENDARY".equals(levels.get(14).defaultRarity()) && levels.get(14).defaultTier() == 3,
            "I-X keep their catalogued data; XI-XV are reserved: Legendary, tier III");
        helper.assertTrue(ImportedGlyphCatalog.ledger().stream().allMatch(id -> catalog.byGlyphId(id).isPresent()),
            "every published identity is still registered");
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_envelope_maxima")
    public static void envelope15_maximaOf1_5_10And15GateTheLiveRange(GameTestHelper helper) {
        if (skip(helper)) return;
        for (int max : new int[]{1, 5, 10, 15}) {
            setMax(FIREBOLT, max);
            GlyphAvailability.Reason at = reason(FIREBOLT, max);
            helper.assertTrue(at == GlyphAvailability.Reason.AVAILABLE, "under a maximum of " + max + " Firebolt " + max + " is usable: " + at);
            if (max < 15) {
                GlyphAvailability.Reason above = reason(FIREBOLT, max + 1);
                helper.assertTrue(above == GlyphAvailability.Reason.LEVEL_UNSUPPORTED,
                    "under a maximum of " + max + " Firebolt " + (max + 1) + " is dormant: " + above);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_envelope_cast", timeoutTicks = 600)
    public static void envelope15_aLevelXVScrollPressesTeachesAndCasts(GameTestHelper helper) {
        if (skip(helper)) return;
        setMax(FIREBOLT, 15);
        GlyphPressBlockEntity press = GlyphPressGameTests.place(helper);
        ServerPlayer presser = GlyphPressGameTests.player(helper, "envelope_press");
        GlyphPressGameTests.load(helper, press, GlyphPressGameTests.scroll(FIREBOLT, 15), GlyphPressGameTests.plates(1),
            GlyphPressGameTests.gems(16));
        press.addSource(10000);
        GlyphPressPlan plan = press.plan(presser);
        helper.assertTrue(plan.ok() && GlyphPressGameTests.isGlyph(plan.output(), FIREBOLT, 15),
            "a Firebolt XV scroll plans Firebolt XV: " + plan.reason() + " " + plan.output());
        helper.assertTrue(GlyphPressBlockEntity.STARTED.equals(press.start(presser)), "the job starts");
        ServerPlayer caster = CastPolicyGameTests.caster(helper, "envelope_cast");
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(2.5, 2.0, 3.5));
        var maxHealth = pig.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(500.0);
        pig.setHealth(500.0f);
        ImportedIronEffect fifteen = glyph(FIREBOLT, 15);
        IPlayerCap cap = CapabilityRegistry.getPlayerDataCap(caster).orElseThrow(IllegalStateException::new);
        ItemStack item = new ItemStack(fifteen.getGlyph());
        caster.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, item);
        item.use(helper.getLevel(), caster, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(cap.knowsGlyph(fifteen), "Firebolt XV teaches like any glyph");
        Spell spell = new Spell(MethodTouch.INSTANCE, fifteen);
        helper.assertTrue(Spell.fromTag(spell.serialize()).recipe.get(1) == fifteen, "a recipe with Firebolt XV round-trips");
        TouchRegressionGameTests.aimAt(caster, pig.getBoundingBox().getCenter());
        ImportedGlyphGameTests.cast(helper, caster, spell);
        helper.succeedWhen(() -> {
            helper.assertTrue(!press.working() && GlyphPressGameTests.isGlyph(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT),
                FIREBOLT, 15), "the Press made exactly Firebolt XV: " + press.describe());
            helper.assertTrue(PaymentTimeline.entries(caster.getUUID()).stream()
                    .anyMatch(e -> e.kind() == PaymentTimeline.Kind.EMISSION && e.detail().contains("final=15")),
                "the cast ran at level 15: " + PaymentTimeline.entries(caster.getUUID()));
            helper.assertTrue(pig.getHealth() < 500.0f, "and the level-15 Firebolt hit");
        });
    }

    @GameTest(template = "platform", batch = "ans_envelope_beyond")
    public static void envelope15_aMaximumBeyondXVAndAPerSpellMaximum(GameTestHelper helper) {
        if (skip(helper)) return;
        int healDefault = definition(HEAL, 1).maxLevel();
        setMax(FIREBOLT, 20);
        helper.assertTrue(reason(FIREBOLT, 15) == GlyphAvailability.Reason.AVAILABLE, "Firebolt XV is usable under a maximum of 20");
        helper.assertTrue(ImportedGlyphCatalog.get().byKey(new ImportedGlyphKey(new ResourceLocation(FIREBOLT), 16)).isEmpty(),
            "levels above the envelope have no identity; they need a larger envelope and a restart");
        helper.assertTrue(glyph(FIREBOLT, 15).getAugmentLimit(AugmentAmplify.INSTANCE.getRegistryName()) == 5,
            "Amplify may still raise a Firebolt XV cast to Iron's maximum of 20");
        GlyphAvailability.Reason healAbove = reason(HEAL, Math.min(15, healDefault + 1));
        helper.assertTrue(healDefault >= 15 || healAbove == GlyphAvailability.Reason.LEVEL_UNSUPPORTED,
            "a per-spell maximum applies to that spell only: Heal above its own maximum stays dormant: " + healAbove);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_envelope_lowered", timeoutTicks = 100)
    public static void envelope15_aLoweredMaximumMakesXVDormantAndRefusesUnpaid(GameTestHelper helper) {
        if (skip(helper)) return;
        setMax(FIREBOLT, 3);
        helper.assertTrue(reason(FIREBOLT, 15) == GlyphAvailability.Reason.LEVEL_UNSUPPORTED, "Firebolt XV is dormant under a maximum of 3");
        helper.assertTrue(ImportedGlyphCatalog.get().registeredMax(new ResourceLocation(FIREBOLT)) == 15, "and still registered");
        ServerPlayer caster = CastPolicyGameTests.caster(helper, "envelope_low");
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(2.5, 2.0, 3.5));
        float health = pig.getHealth();
        TouchRegressionGameTests.aimAt(caster, pig.getBoundingBox().getCenter());
        ImportedGlyphGameTests.cast(helper, caster, new Spell(MethodTouch.INSTANCE, glyph(FIREBOLT, 15)));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(pig.getHealth() == health, "no Firebolt XV ran");
            CastPolicyGameTests.assertNetImportedDebit(helper, caster, 0.0);
            helper.succeed();
        });
    }
}
