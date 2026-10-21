package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.util.ManaUtil;
import com.hollingsworth.arsnouveau.common.capability.IPlayerCap;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectBreak;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.bridge.NativeManaAccess;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.glyph.GlyphCompositionValidator;
import com.otectus.arsnspells.glyph.ImportedGlyphItem;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistration;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.behavior.Dormancy;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Imported glyphs as real Ars parts: registered identity, codec round trips, dormancy, learning
 * and the knowledge-bonus policy. Runs in both profiles; the Iron's-absent profile proves the
 * dormant path.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class ImportedGlyphIdentityGameTests {

    static final ImportedGlyphKey FIREBOLT_3 = new ImportedGlyphKey(ResourceLocation.parse("irons_spellbooks:firebolt"), 3);

    private ImportedGlyphIdentityGameTests() {
    }

    @GameTest(template = "platform", batch = "ans_glyph_identity")
    public static void everyCatalogVariantIsARegisteredArsPartAndItem(GameTestHelper helper) {
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.get();
        helper.assertTrue(catalog.variantCount() == 777, "the shipped catalog must register 777 variants, got " + catalog.variantCount());
        helper.assertTrue(catalog.errors().isEmpty(), "the shipped catalog must load cleanly: " + catalog.errors());
        for (ImportedGlyphKey key : catalog.keys()) {
            AbstractSpellPart part = GlyphRegistry.getSpellPart(key.partId());
            if (!(part instanceof ImportedIronEffect effect) || !effect.key().equals(key)) {
                helper.fail(key.partId() + " is not registered as its imported effect: " + part);
                return;
            }
            Item item = BuiltInRegistries.ITEM.get(key.itemId());
            if (!(item instanceof ImportedGlyphItem glyph) || glyph.effect() != effect || effect.glyphItem != glyph) {
                helper.fail(key.itemId() + " has no matching glyph item: " + item);
                return;
            }
            if (BuiltInRegistries.ITEM.containsKey(key.partId())) {
                helper.fail(key.partId() + " is a spell-part id and must not also be an item id");
                return;
            }
            if (!ImportedGlyphKey.decode(key.partId()).map(key::equals).orElse(false)) {
                helper.fail(key.partId() + " does not decode back to " + key);
                return;
            }
        }
        helper.assertTrue(ImportedGlyphRegistration.effects().size() == catalog.variantCount(), "registration count");
        helper.succeed();
    }

    /** A spell holding an imported glyph keeps it through the Spell codec: never Ars's Break fallback. */
    @GameTest(template = "platform", batch = "ans_glyph_identity")
    public static void spellCodecRoundTripKeepsTheImportedPart(GameTestHelper helper) {
        ImportedIronEffect effect = ImportedGlyphRegistration.effect(FIREBOLT_3);
        Spell spell = new Spell(MethodTouch.INSTANCE, effect);
        CompoundTag tag = CrossCastingHandler.encodeArsSpell(spell);
        Spell decoded = CrossCastingHandler.decodeArsSpell(tag);
        helper.assertTrue(decoded != null && decoded.size() == 2, "decoded spell size");
        helper.assertTrue(decoded.get(1) == effect, "the imported part must decode to itself, got " + decoded.get(1));
        helper.assertTrue(decoded.get(1) != EffectBreak.INSTANCE, "never the unknown-id fallback");
        helper.assertTrue(tag.toString().contains("ars_n_spells:iron/irons_spellbooks/firebolt/level_3"),
            "the spell and level are the saved part id: " + tag);
        helper.succeed();
    }

    /** Without Iron's every imported glyph is registered and dormant for that reason. */
    @GameTest(template = "platform", batch = "ans_glyph_identity")
    public static void dormancyFollowsIronsPresence(GameTestHelper helper) {
        ImportedIronEffect effect = ImportedGlyphRegistration.effect(FIREBOLT_3);
        Dormancy dormancy = GlyphBehavior.variant(FIREBOLT_3).dormancy();
        if (IronsCompat.isLoaded()) {
            helper.assertTrue(dormancy == Dormancy.AVAILABLE, "with Iron's loaded Firebolt III must be available, got " + dormancy);
            helper.assertTrue(effect.isEnabled(), "an available glyph is enabled for Ars");
        } else {
            helper.assertTrue(dormancy == Dormancy.IRONS_ABSENT, "without Iron's the glyph must be dormant (Iron's absent), got " + dormancy);
            helper.assertTrue(!effect.isEnabled(), "a dormant glyph is disabled for Ars");
            var errors = GlyphCompositionValidator.INSTANCE.validate(java.util.List.of(MethodTouch.INSTANCE, effect));
            helper.assertTrue(!errors.isEmpty(), "a spell holding a dormant glyph must not validate");
        }
        helper.succeed();
    }

    /** Learning: once, consuming one glyph; a second use keeps the stack; dormant glyphs never consume. */
    @GameTest(template = "platform", batch = "ans_glyph_identity")
    public static void learningConsumesOnceAndOnlyWhenAvailable(GameTestHelper helper) {
        ImportedIronEffect effect = ImportedGlyphRegistration.effect(FIREBOLT_3);
        ServerPlayer player = player(helper, "glyph_learn");
        IPlayerCap cap = CapabilityRegistry.getPlayerDataCap(player);
        java.util.List<AbstractSpellPart> before = new java.util.ArrayList<>(cap.getKnownGlyphs());
        before.removeIf(p -> p instanceof ImportedIronEffect);
        cap.setKnownGlyphs(before);
        ItemStack stack = new ItemStack(effect.getGlyph(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.use(player.level(), player, InteractionHand.MAIN_HAND);
        if (!effect.behavior().available()) {
            helper.assertTrue(stack.getCount() == 2, "a dormant glyph must not be consumed");
            helper.assertTrue(!cap.knowsGlyph(effect), "a dormant glyph must not be learned");
            helper.succeed();
            return;
        }
        helper.assertTrue(cap.knowsGlyph(effect), "using an available glyph must learn it");
        helper.assertTrue(stack.getCount() == 1, "learning consumes exactly one glyph, count=" + stack.getCount());
        stack.use(player.level(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(stack.getCount() == 1, "a known glyph is not consumed again, count=" + stack.getCount());
        helper.succeed();
    }

    /** Ten variants of one spell add one glyph's worth of mana under the default policy. */
    @GameTest(template = "platform", batch = "ans_glyph_identity")
    public static void levelVariantsOfOneSpellCountOnceForTheManaBonus(GameTestHelper helper) {
        ServerPlayer player = player(helper, "glyph_bonus");
        IPlayerCap cap = CapabilityRegistry.getPlayerDataCap(player);
        java.util.List<AbstractSpellPart> base = new java.util.ArrayList<>(cap.getKnownGlyphs());
        base.removeIf(p -> p instanceof ImportedIronEffect);
        cap.setKnownGlyphs(base);
        var mana = NativeManaAccess.with(player, ResourceUnit.ARS_MANA, () -> CapabilityRegistry.getMana(player));
        mana.setGlyphBonus(base.size());
        int baseline = NativeManaAccess.with(player, ResourceUnit.ARS_MANA, () -> ManaUtil.getMaxMana(player));

        java.util.List<AbstractSpellPart> withVariants = new java.util.ArrayList<>(base);
        for (int level = 1; level <= 10; level++) {
            withVariants.add(ImportedGlyphRegistration.effect(new ImportedGlyphKey(FIREBOLT_3.ironSpellId(), level)));
        }
        withVariants.add(ImportedGlyphRegistration.effect(new ImportedGlyphKey(ResourceLocation.parse("irons_spellbooks:icicle"), 1)));
        cap.setKnownGlyphs(withVariants);
        mana.setGlyphBonus(withVariants.size());
        int twoSpells = NativeManaAccess.with(player, ResourceUnit.ARS_MANA, () -> ManaUtil.getMaxMana(player));
        int perGlyph = com.hollingsworth.arsnouveau.setup.config.ServerConfig.GLYPH_MAX_BONUS.get();
        helper.assertTrue(twoSpells - baseline == 2 * perGlyph, "eleven variants of two spells must add two glyphs' worth ("
            + 2 * perGlyph + "), added " + (twoSpells - baseline));
        // Repeating the read changes nothing: the policy subtracts at read time, never from stored state.
        int again = NativeManaAccess.with(player, ResourceUnit.ARS_MANA, () -> ManaUtil.getMaxMana(player));
        helper.assertTrue(again == twoSpells, "repeated reads must be stable");
        helper.assertTrue(cap.getKnownGlyphs().size() == withVariants.size(), "every variant stays learned");
        cap.setKnownGlyphs(base);
        mana.setGlyphBonus(base.size());
        helper.succeed();
    }

    /** Self-only glyphs refuse Touch at composition time, with a reason, before any cast. */
    @GameTest(template = "platform", batch = "ans_glyph_identity")
    public static void compositionRefusesAFormThatCannotReachTheTarget(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ImportedIronEffect heal = ImportedGlyphRegistration.effect(new ImportedGlyphKey(ResourceLocation.parse("irons_spellbooks:heal"), 1));
        helper.assertTrue(!GlyphCompositionValidator.INSTANCE.validate(java.util.List.of(MethodTouch.INSTANCE, heal)).isEmpty(),
            "Touch -> Iron Heal must be refused: Heal only ever heals its caster");
        helper.assertTrue(GlyphCompositionValidator.INSTANCE.validate(java.util.List.of(MethodSelf.INSTANCE, heal)).isEmpty(),
            "Self -> Iron Heal is a valid composition");
        helper.succeed();
    }

    static ServerPlayer player(GameTestHelper helper, String scenario) {
        String name = scenario.length() > 16 ? scenario.substring(0, 16) : scenario;
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
            UUID.nameUUIDFromBytes(("ans_gametest/" + scenario).getBytes(StandardCharsets.UTF_8)), name));
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 2.0, 2.5)));
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return player;
    }
}
