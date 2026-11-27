package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.capability.IPlayerCap;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.otectus.arsnspells.block.GlyphPressBlockEntity;
import com.otectus.arsnspells.casting.PaymentTimeline;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistration;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.behavior.Dormancy;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import com.otectus.arsnspells.network.GlyphPressActionPayload;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 3.4.1 review CATALOG-01 on NeoForge: the {@code irons_envelope_15} catalog package and Iron's maximum
 * levels above X. The same scenarios as the Forge build's class of the same name: XI-XV reserved under
 * the shipped contracts; maxima of 1, 5, 10 and 15 gate exactly the levels above them; a Firebolt XV
 * scroll presses, teaches, round-trips and casts at level 15; a maximum above XV leaves XVI and up
 * without identities while Amplify still reaches Iron's maximum; a per-spell maximum applies to that
 * spell only; a lowered maximum makes XV dormant and refuses it unpaid. Each changes Iron's maximum,
 * so each owns its batch. Runs with {@code -PwithCatalogPackages=irons_envelope_15}; skips without it.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class LevelEnvelopeGameTests {
    static final String PACKAGE = "ans_catalog_irons_envelope_15";
    private static final ResourceLocation FIREBOLT = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "firebolt");
    private static final ResourceLocation HEAL = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "heal");
    private static volatile int fireboltBefore = -1;

    private LevelEnvelopeGameTests() {}

    private static boolean skip(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return true;
        return OptionalModGate.skipIfAbsent(helper, PACKAGE);
    }

    private static Dormancy dormancy(ResourceLocation spell, int level) {
        return GlyphBehavior.current().variant(new ImportedGlyphKey(spell, level)).dormancy();
    }

    private static ImportedIronEffect glyph(ResourceLocation spell, int level) {
        return ImportedGlyphRegistration.effect(new ImportedGlyphKey(spell, level));
    }

    private static void setFireboltMax(int max) {
        int previous = Glyph341IronsSupport.setMaxLevel(FIREBOLT.toString(), max);
        if (fireboltBefore < 0) fireboltBefore = previous;
        GlyphBehavior.rebuild();
    }

    private static void restore() {
        if (!IronsCompat.isLoaded() || fireboltBefore < 0) return;
        Glyph341IronsSupport.setMaxLevel(FIREBOLT.toString(), fireboltBefore);
        fireboltBefore = -1;
        GlyphBehavior.rebuild();
    }

    @AfterBatch(batch = "ans_envelope_maxima") public static void afterMaxima(ServerLevel level) { restore(); }
    @AfterBatch(batch = "ans_envelope_cast") public static void afterCast(ServerLevel level) { restore(); }
    @AfterBatch(batch = "ans_envelope_beyond") public static void afterBeyond(ServerLevel level) { restore(); }
    @AfterBatch(batch = "ans_envelope_lowered") public static void afterLowered(ServerLevel level) { restore(); }

    @GameTest(template = "platform", batch = "ans_envelope_identities")
    public static void envelope15_reservesXIToXVUnderTheShippedContract(GameTestHelper helper) {
        if (skip(helper)) return;
        for (CatalogEntry entry : ImportedGlyphCatalog.get().entries()) {
            if (!"irons_spellbooks".equals(entry.spellId().getNamespace())) continue;
            helper.assertTrue(entry.minLevel() == 1 && entry.registeredMax() == 15, entry.spellId() + " registers I-XV");
        }
        CatalogEntry firebolt = ImportedGlyphCatalog.get().entry(FIREBOLT);
        helper.assertTrue(firebolt.executable() && firebolt.required() && firebolt.adapter().toString().equals("ars_n_spells:projectile"),
            "Firebolt keeps the shipped projectile contract: " + firebolt.adapter());
        helper.assertTrue(firebolt.sources().stream().anyMatch(s -> s.contains(PACKAGE)), "the package extends it: " + firebolt.sources());
        helper.assertTrue(!firebolt.reserved(10) && firebolt.reserved(11) && "LEGENDARY".equals(firebolt.rarityFor(15)) && firebolt.defaultTierFor(15) == 3,
            "I-X keep their catalogued data; XI-XV are reserved: Legendary, tier III");
        helper.assertTrue(ImportedGlyphCatalog.ledger().stream().allMatch(id -> ImportedGlyphCatalog.get().keyForPart(id) != null),
            "every published identity is still registered");
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_envelope_maxima")
    public static void envelope15_maximaOf1_5_10And15GateTheLiveRange(GameTestHelper helper) {
        if (skip(helper)) return;
        for (int max : new int[]{1, 5, 10, 15}) {
            setFireboltMax(max);
            Dormancy at = dormancy(FIREBOLT, max);
            helper.assertTrue(at == Dormancy.AVAILABLE, "under a maximum of " + max + " Firebolt " + max + " is usable: " + at);
            if (max < 15) {
                Dormancy above = dormancy(FIREBOLT, max + 1);
                helper.assertTrue(above == Dormancy.LEVEL_ABOVE_SERVER_MAX, "under a maximum of " + max + " Firebolt " + (max + 1) + " is dormant: " + above);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_envelope_cast", timeoutTicks = 600)
    public static void envelope15_aLevelXVScrollPressesTeachesAndCasts(GameTestHelper helper) {
        if (skip(helper)) return;
        setFireboltMax(15);
        GlyphPressBlockEntity press = GlyphPressGameTests.press(helper);
        ServerPlayer presser = GlyphPressGameTests.player(helper, "envelope_press");
        ImportedGlyphKey fifteenKey = new ImportedGlyphKey(FIREBOLT, 15);
        GlyphPressGameTests.load(press, IronGlyphBridges.get().scrollStack(FIREBOLT, 15), 1,
            GlyphBehavior.current().variant(fifteenKey).pressGems());
        GlyphPressGameTests.fillSource(press, 10000);
        String outcome = GlyphPressGameTests.request(presser, GlyphPressGameTests.open(presser, press), GlyphPressActionPayload.ACTION_INSCRIBE);
        helper.assertTrue(press.job() != null, "the Firebolt XV job starts: " + outcome);
        ServerPlayer caster = CastReviewGameTests.caster(helper, "envelope_cast");
        Pig pig = CastReviewGameTests.sturdyPig(helper, new Vec3(3.5, 2.0, 2.5));
        float health = pig.getHealth();
        ImportedIronEffect fifteen = glyph(FIREBOLT, 15);
        IPlayerCap cap = CapabilityRegistry.getPlayerDataCap(caster);
        ItemStack item = new ItemStack(fifteen.getGlyph());
        caster.setItemInHand(InteractionHand.MAIN_HAND, item);
        item.use(caster.level(), caster, InteractionHand.MAIN_HAND);
        helper.assertTrue(cap.knowsGlyph(fifteen), "Firebolt XV teaches like any glyph");
        Spell spell = ImportedGlyphGameTests.spell(MethodTouch.INSTANCE, fifteen);
        Tag encoded = Spell.CODEC.codec().encodeStart(NbtOps.INSTANCE, spell).result().orElseThrow();
        helper.assertTrue(Spell.CODEC.codec().parse(NbtOps.INSTANCE, encoded).result().orElseThrow().unsafeList().get(1) == fifteen,
            "a recipe with Firebolt XV round-trips");
        ImportedGlyphGameTests.aim(caster, pig.getBoundingBox().getCenter());
        ImportedGlyphGameTests.cast(caster, spell);
        helper.succeedWhen(() -> {
            helper.assertTrue(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT).is(fifteen.getGlyph()), "the Press made exactly Firebolt XV");
            helper.assertTrue(PaymentTimeline.entries(caster.getUUID()).stream()
                    .anyMatch(e -> e.kind() == PaymentTimeline.Kind.NATIVE_EFFECT && e.detail().contains("level=15")),
                "the cast ran at level 15: " + PaymentTimeline.entries(caster.getUUID()));
            helper.assertTrue(pig.getHealth() < health, "and the level-15 Firebolt hit");
        });
    }

    @GameTest(template = "platform", batch = "ans_envelope_beyond")
    public static void envelope15_aMaximumBeyondXVAndAPerSpellMaximum(GameTestHelper helper) {
        if (skip(helper)) return;
        int healDefault = ImportedGlyphCatalog.get().entry(HEAL).maxLevel();
        setFireboltMax(20);
        helper.assertTrue(dormancy(FIREBOLT, 15) == Dormancy.AVAILABLE, "Firebolt XV is usable under a maximum of 20");
        helper.assertTrue(!ImportedGlyphCatalog.get().contains(new ImportedGlyphKey(FIREBOLT, 16)),
            "levels above the envelope have no identity; they need a larger envelope and a restart");
        // NeoForge bounds Amplify to MAX_AMPLIFY_LEVELS per glyph as well (a documented loader difference).
        helper.assertTrue(glyph(FIREBOLT, 15).getAugmentLimit(AugmentAmplify.INSTANCE.getRegistryName())
                == Math.min(ImportedIronEffect.MAX_AMPLIFY_LEVELS, 5),
            "Amplify may still raise a Firebolt XV cast toward Iron's maximum of 20, within NeoForge's per-glyph bound");
        Dormancy healAbove = dormancy(HEAL, Math.min(15, healDefault + 1));
        helper.assertTrue(healDefault >= 15 || healAbove == Dormancy.LEVEL_ABOVE_SERVER_MAX,
            "a per-spell maximum applies to that spell only: Heal above its own maximum stays dormant: " + healAbove);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_envelope_lowered", timeoutTicks = 100)
    public static void envelope15_aLoweredMaximumMakesXVDormantAndRefusesUnpaid(GameTestHelper helper) {
        if (skip(helper)) return;
        setFireboltMax(3);
        helper.assertTrue(dormancy(FIREBOLT, 15) == Dormancy.LEVEL_ABOVE_SERVER_MAX, "Firebolt XV is dormant under a maximum of 3");
        helper.assertTrue(ImportedGlyphCatalog.get().entry(FIREBOLT).registeredMax() == 15, "and still registered");
        ServerPlayer caster = CastReviewGameTests.caster(helper, "envelope_low");
        Pig pig = CastReviewGameTests.sturdyPig(helper, new Vec3(3.5, 2.0, 2.5));
        float health = pig.getHealth();
        ImportedGlyphGameTests.aim(caster, pig.getBoundingBox().getCenter());
        ImportedGlyphGameTests.cast(caster, ImportedGlyphGameTests.spell(MethodTouch.INSTANCE, glyph(FIREBOLT, 15)));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(pig.getHealth() == health, "no Firebolt XV ran");
            CastReviewGameTests.assertNetImportedDebit(helper, caster, 0.0);
            helper.succeed();
        });
    }
}
