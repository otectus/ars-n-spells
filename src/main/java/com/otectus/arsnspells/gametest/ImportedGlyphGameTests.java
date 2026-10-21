package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.capability.IPlayerCap;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectInvisibility;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.glyph.GlyphAvailability;
import com.otectus.arsnspells.glyph.GlyphSessions;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistrar;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.util.ArsSpellIntegrity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 3.4.0: imported Iron's glyphs as real Ars parts, cast from a real Ars spell book.
 *
 * <p>Every cast goes through {@code SpellBook.use} by way of {@code ServerPlayerGameMode.useItem},
 * so Ars's own validator, cast event, form dispatch and resolver drive the imported glyph, and the
 * observable is the world: a healed caster, a slowed or burnt mob, a projectile, a charged cast
 * that holds the rest of the recipe until it completes.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class ImportedGlyphGameTests {
    private static final String FIREBOLT = "irons_spellbooks:firebolt";

    // ---- Identity and dormancy (every profile) --------------------------------------------

    @GameTest(template = "platform")
    public static void registration_everyCatalogGlyphIsAnArsPartWithAnItem(GameTestHelper helper) {
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.get();
        helper.assertTrue(catalog.size() == 777, "the shipped catalog registers 777 glyphs, got " + catalog.size());
        for (ImportedGlyphDefinition definition : catalog.definitions()) {
            AbstractSpellPart part = GlyphRegistry.getSpellPart(definition.glyphId());
            helper.assertTrue(part instanceof ImportedIronEffect, definition.glyphId() + " must be an imported Ars part");
            helper.assertTrue(ForgeRegistries.ITEMS.containsKey(definition.glyphId()), definition.glyphId() + " must have its glyph item");
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void serialization_importedPartSurvivesASpellRoundTrip(GameTestHelper helper) {
        ImportedIronEffect firebolt = glyph("irons_spellbooks:firebolt", 3);
        Spell spell = new Spell(MethodTouch.INSTANCE, firebolt, AugmentAmplify.INSTANCE);
        var tag = spell.serialize();
        helper.assertTrue(ArsSpellIntegrity.missingGlyphIds(tag).isEmpty(), "every imported id must decode, never as missing");
        Spell decoded = Spell.fromTag(tag);
        helper.assertTrue(decoded.recipe.size() == 3 && decoded.recipe.get(1) == firebolt,
            "the decoded recipe must hold the same imported part at the same level, got " + decoded.recipe);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void dormancy_followsIronsPresence(GameTestHelper helper) {
        ImportedGlyphDefinition heal = glyph("irons_spellbooks:heal", 1).definition();
        GlyphAvailability.Status status = GlyphAvailability.status(heal);
        if (IronsCompat.isLoaded()) {
            helper.assertTrue(status.available(), "Heal I must be available with Iron's loaded, got " + status.reason());
        } else {
            helper.assertTrue(status.reason() == GlyphAvailability.Reason.IRONS_ABSENT,
                "without Iron's every imported glyph must be dormant, got " + status.reason());
            helper.assertTrue(!glyph("irons_spellbooks:heal", 1).isEnabled(), "a dormant glyph is hidden and skipped by Ars");
        }
        helper.succeed();
    }

    // ---- Learning --------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void ironsLoaded_learning_consumesOneGlyphOnceAndKeepsKnownOnes(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_learn");
        // Survival: in creative Minecraft restores a used stack's count, so nothing could be observed.
        player.setGameMode(GameType.SURVIVAL);
        ImportedIronEffect heal = glyph("irons_spellbooks:heal", 2);
        IPlayerCap cap = CapabilityRegistry.getPlayerDataCap(player).orElseThrow(IllegalStateException::new);
        helper.assertTrue(!cap.knowsGlyph(heal), "a fresh player must not know the glyph");
        ItemStack stack = new ItemStack(heal.getGlyph(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.gameMode.useItem(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND);
        helper.assertTrue(cap.knowsGlyph(heal), "using the glyph must teach it");
        helper.assertTrue(stack.getCount() == 1, "learning must consume exactly one glyph, left " + stack.getCount());
        player.gameMode.useItem(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND);
        helper.assertTrue(stack.getCount() == 1, "a known glyph must not be consumed again");
        helper.assertTrue(!cap.knowsGlyph(glyph("irons_spellbooks:heal", 3)), "learning Heal II must not grant Heal III");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_learning_respectsIronsOwnLearningRequirement(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_learn_eld");
        player.setGameMode(GameType.SURVIVAL);
        ImportedIronEffect shroud = glyph("irons_spellbooks:abyssal_shroud", 1);
        IPlayerCap cap = CapabilityRegistry.getPlayerDataCap(player).orElseThrow(IllegalStateException::new);
        ItemStack stack = new ItemStack(shroud.getGlyph());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.gameMode.useItem(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND);
        helper.assertTrue(!cap.knowsGlyph(shroud) && stack.getCount() == 1,
            "an eldritch glyph must not be learned before the spell is learned in Iron's, and must not be consumed");
        IronsGlyphTestSupport.learn(player, "irons_spellbooks:abyssal_shroud");
        player.gameMode.useItem(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND);
        helper.assertTrue(cap.knowsGlyph(shroud), "once learned in Iron's, the glyph can be learned in Ars");
        helper.succeed();
    }

    // ---- Casting from a native Ars book -----------------------------------------------------

    @GameTest(template = "platform")
    public static void ironsLoaded_selfHeal_healsTheCasterAndStartsIronsCooldown(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_self_heal");
        player.setGameMode(GameType.SURVIVAL);
        TouchRegressionGameTests.fundArs(player);
        IronsGlyphTestSupport.clearCooldowns(player);
        cast(helper, player, new Spell(MethodSelf.INSTANCE, glyph("irons_spellbooks:heal", 1)));
        helper.assertTrue(player.getHealth() > 10.0f, "Self -> Iron's Heal must heal the caster, health " + player.getHealth());
        helper.assertTrue(IronsGlyphTestSupport.onCooldown(player, "irons_spellbooks:heal"),
            "the imported Heal must start Iron's own cooldown for irons_spellbooks:heal");
        helper.assertTrue(GlyphSessions.activeFor(player.getUUID()) == 0, "a finished instant cast leaves no open session");
        float healed = player.getHealth();
        player.setHealth(10.0f);
        cast(helper, player, new Spell(MethodSelf.INSTANCE, glyph("irons_spellbooks:heal", 2)));
        helper.assertTrue(player.getHealth() == 10.0f,
            "a different level of the same Iron's spell shares its cooldown: Heal II must be refused while Heal is cooling down (first cast healed to " + healed + ")");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_touchSlow_slowsTheMobItWasAimedAt(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_touch_slow");
        Pig pig = TouchRegressionGameTests.pigInFront(helper);
        TouchRegressionGameTests.aimAt(player, pig.getBoundingBox().getCenter());
        IronsGlyphTestSupport.clearCooldowns(player);
        cast(helper, player, new Spell(MethodTouch.INSTANCE, glyph("irons_spellbooks:slow", 1)));
        // Slow is a long cast: it charges in Iron's own slot and lands when the charge completes.
        helper.assertTrue(!IronsGlyphTestSupport.slowed(pig), "a long cast must not land before its charge completes");
        IronsProxyCastDriver.tickUntilCastEnds(player);
        helper.assertTrue(IronsGlyphTestSupport.slowed(pig), "Touch -> Iron's Slow must slow the mob Touch resolved");
        helper.assertTrue(!IronsGlyphTestSupport.slowed(player), "Touch -> Iron's Slow must not slow the caster");
        helper.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void ironsLoaded_touchChainLightning_strikesTheMobItWasAimedAt(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_touch_chain");
        Pig pig = TouchRegressionGameTests.pigInFront(helper);
        TouchRegressionGameTests.aimAt(player, pig.getBoundingBox().getCenter());
        IronsGlyphTestSupport.clearCooldowns(player);
        float before = pig.getHealth();
        cast(helper, player, new Spell(MethodTouch.INSTANCE, glyph("irons_spellbooks:chain_lightning", 1)));
        helper.assertTrue(player.getHealth() == 10.0f, "the chain must not start at the caster");
        helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() < before || pig.isRemoved(),
            "Touch -> Iron's Chain Lightning (an instant targeted spell) must strike the mob Touch resolved"));
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void ironsLoaded_touchFirebolt_deliversItsProjectileToTheMob(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_touch_bolt");
        Pig pig = TouchRegressionGameTests.pigInFront(helper);
        TouchRegressionGameTests.aimAt(player, pig.getBoundingBox().getCenter());
        IronsGlyphTestSupport.clearCooldowns(player);
        float before = pig.getHealth();
        cast(helper, player, new Spell(MethodTouch.INSTANCE, glyph("irons_spellbooks:firebolt", 1)));
        helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() < before || pig.isRemoved() || pig.isOnFire(),
            "the Firebolt must reach and hurt the mob Touch resolved"));
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_selfFirebolt_launchesAlongTheCastersAim(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_self_bolt");
        TouchRegressionGameTests.lookAtSky(player);
        IronsGlyphTestSupport.clearCooldowns(player);
        AABB around = new AABB(player.blockPosition()).inflate(4);
        int before = IronsGlyphTestSupport.magicProjectilesNear(player, around);
        cast(helper, player, new Spell(MethodSelf.INSTANCE, glyph("irons_spellbooks:firebolt", 1)));
        helper.assertTrue(IronsGlyphTestSupport.magicProjectilesNear(player, around) > before, "Self -> Firebolt must launch the native projectile");
        // The predicate lives in the Iron's-only helper: a lambda over an Iron's type here would put
        // that type in this class's method signatures, which Forge's test scan resolves without Iron's.
        helper.assertTrue(IronsGlyphTestSupport.anyRisingProjectileOwnedBy(player, around),
            "a Self Firebolt flies along the caster's own aim (straight up here) and is owned by the caster");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_casterOnlyGlyph_withTouch_isRefused(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_touch_heal");
        Pig pig = TouchRegressionGameTests.pigInFront(helper);
        TouchRegressionGameTests.aimAt(player, pig.getBoundingBox().getCenter());
        IronsGlyphTestSupport.clearCooldowns(player);
        Spell spell = new Spell(MethodTouch.INSTANCE, glyph("irons_spellbooks:heal", 1));
        var errors = com.hollingsworth.arsnouveau.api.ArsNouveauAPI.getInstance().getSpellCraftingSpellValidator().validate(spell.recipe);
        helper.assertTrue(!errors.isEmpty(), "the editor must reject Touch -> Iron's Heal (Self only)");
        cast(helper, player, spell);
        helper.assertTrue(player.getHealth() == 10.0f, "Touch -> Iron's Heal must not silently heal the caster");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_survival_importedLegIsChargedOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_paid_once");
        player.setGameMode(GameType.SURVIVAL);
        TouchRegressionGameTests.fundArs(player);
        IronsGlyphTestSupport.clearCooldowns(player);
        double before = TouchRegressionGameTests.arsMana(player);
        int ironsCost = IronsGlyphTestSupport.manaCost("irons_spellbooks:heal", 1);
        cast(helper, player, new Spell(MethodSelf.INSTANCE, glyph("irons_spellbooks:heal", 1)));
        double spent = before - TouchRegressionGameTests.arsMana(player);
        double multiplier = com.otectus.arsnspells.config.AnsConfig.CROSS_CAST_COST_MULTIPLIER.get();
        // iss_primary at rate 1: the Self form's Ars cost converted, plus Heal's Iron's cost times the multiplier, once.
        double formCost = MethodSelf.INSTANCE.getCastingCost();
        double expected = formCost + ironsCost * multiplier;
        helper.assertTrue(Math.abs(spent - expected) < 1.01,
            "expected " + expected + " (Self " + formCost + " + Heal " + ironsCost + " x " + multiplier + "), spent " + spent);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_training_creditsTheRootCastOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_training");
        IronsGlyphTestSupport.clearCooldowns(player);
        Spell spell = new Spell(MethodSelf.INSTANCE, glyph("irons_spellbooks:heal", 1));
        String school = com.otectus.arsnspells.util.SpellAnalysis.analyze(spell).schoolKey();
        helper.assertTrue("irons_spellbooks:holy".equals(school), "an imported Heal belongs to Iron's Holy school, got " + school);
        int before = TouchRegressionGameTests.affinity(player, spell);
        cast(helper, player, spell);
        int gained = TouchRegressionGameTests.affinity(player, spell) - before;
        helper.assertTrue(gained == 1, "one root cast must credit its school exactly once, got " + gained);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_knowledgeBonus_levelsOfOneSpellCountOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_bonus");
        IPlayerCap cap = CapabilityRegistry.getPlayerDataCap(player).orElseThrow(IllegalStateException::new);
        var mana = CapabilityRegistry.getMana(player).orElseThrow(IllegalStateException::new);
        int perGlyph = com.hollingsworth.arsnouveau.setup.config.ServerConfig.GLYPH_MAX_BONUS.get();
        mana.setGlyphBonus(cap.getKnownGlyphs().size());
        int baseline = com.hollingsworth.arsnouveau.api.util.ManaUtil.getMaxMana(player);
        for (int level = 1; level <= 3; level++) cap.unlockGlyph(glyph("irons_spellbooks:firebolt", level));
        // What Ars does when the player next uses a spell book: count every known glyph.
        mana.setGlyphBonus(cap.getKnownGlyphs().size());
        int perSpell = com.hollingsworth.arsnouveau.api.util.ManaUtil.getMaxMana(player);
        helper.assertTrue(perSpell - baseline == perGlyph,
            "three levels of Firebolt must add one glyph's bonus (" + perGlyph + ") under per_spell, added " + (perSpell - baseline));
        helper.assertTrue(com.hollingsworth.arsnouveau.api.util.ManaUtil.getMaxMana(player) == perSpell,
            "the adjusted maximum must be stable across recalculation");
        cap.unlockGlyph(glyph("irons_spellbooks:heal", 1));
        mana.setGlyphBonus(cap.getKnownGlyphs().size());
        helper.assertTrue(com.hollingsworth.arsnouveau.api.util.ManaUtil.getMaxMana(player) - perSpell == perGlyph,
            "a different Iron's spell counts as a glyph of its own");
        // native_per_variant is covered by ImportedGlyphKnowledgeTest: writing a SERVER config value
        // here would trigger Forge's asynchronous file reload under other tests' settings.
        helper.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_longFireball_holdsTheRecipeUntilItsNativeCastCompletes(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_long_ball");
        Pig pig = TouchRegressionGameTests.pigInFront(helper);
        TouchRegressionGameTests.aimAt(player, pig.getBoundingBox().getCenter());
        IronsGlyphTestSupport.clearCooldowns(player);
        cast(helper, player, new Spell(MethodTouch.INSTANCE, glyph("irons_spellbooks:fireball", 1), EffectInvisibility.INSTANCE));
        helper.assertTrue(IronsGlyphTestSupport.casting(player)
                && "irons_spellbooks:fireball".equals(IronsGlyphTestSupport.castingSpell(player)),
            "Fireball is a long cast: it must be charging in Iron's own cast slot");
        helper.assertTrue(!pig.hasEffect(MobEffects.INVISIBILITY), "the rest of the recipe must wait for the charge");
        IronsProxyCastDriver.tickUntilCastEnds(player);
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.hasEffect(MobEffects.INVISIBILITY),
                "once the Fireball completes, the rest of the recipe resumes at the same target");
            helper.assertTrue(GlyphSessions.activeFor(player.getUUID()) == 0, "and its session closes");
        });
    }

    // ---- Catalog coverage against the loaded registry ---------------------------------------

    /**
     * Every player spell in the loaded Iron's registry has a glyph for every level it allows, so
     * a spell added to a pinned build cannot be silently left out of the catalog.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_catalogCoversEveryLoadedSpellAtEveryLevel(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        java.util.List<String> missing = new java.util.ArrayList<>();
        java.util.Map<ResourceLocation, Integer> live = IronsGlyphTestSupport.playerSpells();
        for (var entry : live.entrySet()) {
            var levels = ImportedGlyphCatalog.get().levelsOf(entry.getKey());
            if (levels.size() != entry.getValue()) missing.add(entry.getKey() + " has " + levels.size() + " of " + entry.getValue() + " levels");
            for (int level = 1; level <= entry.getValue(); level++)
                if (GlyphRegistry.getSpellPart(new ImportedGlyphKey(entry.getKey(), level).glyphId()) == null)
                    missing.add(entry.getKey() + " level " + level + " is not a registered glyph");
        }
        helper.assertTrue(missing.isEmpty(), "uncovered Iron's spells: " + missing);
        // The catalog is the union of the pinned builds. A catalogued spell this build does not
        // register must come only from another pinned build, and is dormant here, not mis-cast.
        String build = net.minecraftforge.fml.ModList.get().getModContainerById(IronsCompat.MODID)
            .map(container -> container.getModInfo().getVersion().toString()).orElse("");
        java.util.List<String> unexplained = new java.util.ArrayList<>();
        for (ResourceLocation spell : ImportedGlyphCatalog.get().spells()) {
            ImportedGlyphDefinition first = ImportedGlyphCatalog.get().levelsOf(spell).get(0);
            if (live.containsKey(spell)) {
                if (ImportedGlyphCatalog.get().ironsBuilds().contains(build) && !first.ironsVersions().contains(build))
                    unexplained.add(spell + " is registered by " + build + " but not catalogued for it");
            } else if (first.ironsVersions().contains(build)
                    || GlyphAvailability.status(first).reason() != GlyphAvailability.Reason.SPELL_MISSING) {
                unexplained.add(spell + " is catalogued for " + first.ironsVersions() + " but not registered, status "
                    + GlyphAvailability.status(first).reason());
            }
        }
        helper.assertTrue(unexplained.isEmpty(), "catalog and loaded registry disagree: " + unexplained);
        helper.succeed();
    }

    /**
     * One representative per adapter family, at its minimum and maximum level: both glyphs exist,
     * the Press makes exactly them from the matching scroll, and the levels just outside do not exist.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_everyFamilyAtItsMinimumAndMaximumLevel(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        String[] representatives = {"irons_spellbooks:heal", "irons_spellbooks:slow", FIREBOLT,
            "irons_spellbooks:teleport", "irons_spellbooks:summon_vex"};
        java.util.Set<com.otectus.arsnspells.glyph.GlyphFamily> families = java.util.EnumSet.noneOf(com.otectus.arsnspells.glyph.GlyphFamily.class);
        for (String id : representatives) {
            ResourceLocation spell = new ResourceLocation(id);
            var levels = ImportedGlyphCatalog.get().levelsOf(spell);
            helper.assertTrue(!levels.isEmpty(), id + " must be catalogued");
            families.add(levels.get(0).family());
            int max = IronsGlyphTestSupport.maxLevel(id);
            helper.assertTrue(levels.size() == max, id + " must have a glyph per level 1-" + max);
            for (int level : new int[]{1, max}) {
                ItemStack scroll = GlyphPressGameTests.scroll(id, level);
                var plan = com.otectus.arsnspells.glyph.recipe.GlyphPressPlanner.plan(scroll, GlyphPressGameTests.plates(1),
                    GlyphPressGameTests.gems(64), ItemStack.EMPTY, null, 100000);
                helper.assertTrue(plan.ok() && GlyphPressGameTests.isGlyph(plan.output(), id, level),
                    id + " level " + level + " must press into exactly its glyph, got " + plan.reason());
            }
            helper.assertTrue(ImportedGlyphCatalog.get().byKey(new ImportedGlyphKey(spell, max + 1)).isEmpty(),
                id + " level " + (max + 1) + " must not exist");
            helper.assertTrue(ImportedGlyphKey.decode(new ResourceLocation("ars_n_spells", "iron/" + spell.getNamespace() + "/"
                + spell.getPath() + "/level_0")).isEmpty(), "level 0 is not a glyph id");
        }
        helper.assertTrue(families.size() == com.otectus.arsnspells.glyph.GlyphFamily.values().length,
            "the representatives must cover every adapter family, covered " + families);
        helper.succeed();
    }

    /** A channelled aimed glyph drains the mob Touch hit through Iron's own pulses, and the recipe resumes when the channel ends. */
    @GameTest(template = "platform", timeoutTicks = 300)
    public static void ironsLoaded_touchRayOfSiphoning_channelsIntoTheMobThenResumes(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_channel");
        Pig pig = TouchRegressionGameTests.pigInFront(helper);
        // Enough health to outlast the whole channel, so the continuation can be observed.
        pig.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(400.0);
        pig.setHealth(400.0f);
        TouchRegressionGameTests.aimAt(player, pig.getBoundingBox().getCenter());
        IronsGlyphTestSupport.clearCooldowns(player);
        cast(helper, player, new Spell(MethodTouch.INSTANCE, glyph("irons_spellbooks:ray_of_siphoning", 1), EffectInvisibility.INSTANCE));
        helper.assertTrue("irons_spellbooks:ray_of_siphoning".equals(IronsGlyphTestSupport.castingSpell(player)),
            "Ray of Siphoning channels in Iron's own cast slot");
        helper.assertTrue(!pig.hasEffect(MobEffects.INVISIBILITY), "the rest of the recipe waits for the channel");
        IronsProxyCastDriver.tickUntilCastEnds(player);
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.getHealth() < 400.0f, "the channel's pulses must drain the mob Touch hit");
            helper.assertTrue(pig.hasEffect(MobEffects.INVISIBILITY), "when the channel ends the recipe resumes at the same target");
            helper.assertTrue(GlyphSessions.activeFor(player.getUUID()) == 0, "and the session closes");
        });
    }

    /** Projectile delivers an imported target effect: Chain Lightning starts at the mob the projectile hit. */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void ironsLoaded_projectileChainLightning_strikesTheMobTheProjectileHit(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_proj_chain");
        Pig pig = TouchRegressionGameTests.pigInFront(helper);
        TouchRegressionGameTests.aimAt(player, pig.getBoundingBox().getCenter());
        IronsGlyphTestSupport.clearCooldowns(player);
        float before = pig.getHealth();
        cast(helper, player, new Spell(com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile.INSTANCE,
            glyph("irons_spellbooks:chain_lightning", 1)));
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.getHealth() < before || pig.isRemoved(), "the chain must strike the mob the projectile hit");
            helper.assertTrue(player.getHealth() == 10.0f, "and never the caster");
        });
    }

    /** A position-based spell: Touch at a block calls Iron's Lightning Bolt down on that block. */
    @GameTest(template = "platform")
    public static void ironsLoaded_touchLightningBolt_strikesTheBlockTouchHit(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_touch_bolt2");
        net.minecraft.core.BlockPos dirt = TouchRegressionGameTests.dirtInFront(helper);
        Vec3 target = helper.absoluteVec(Vec3.atCenterOf(dirt));
        TouchRegressionGameTests.aimAt(player, target);
        IronsGlyphTestSupport.clearCooldowns(player);
        cast(helper, player, new Spell(MethodTouch.INSTANCE, glyph("irons_spellbooks:lightning_bolt", 1)));
        var bolts = helper.getLevel().getEntities(net.minecraft.world.entity.EntityType.LIGHTNING_BOLT,
            new AABB(net.minecraft.core.BlockPos.containing(target)).inflate(16), bolt -> true);
        helper.assertTrue(!bolts.isEmpty(), "Touch -> Lightning Bolt must call a bolt down");
        helper.assertTrue(bolts.stream().allMatch(bolt -> bolt.position().distanceTo(target) < 2.0),
            "the bolt must strike the block Touch hit, not the caster: " + bolts.stream().map(b -> b.position().toString()).toList());
        helper.succeed();
    }

    /**
     * The augment matrix: only Amplify and Dampen, each only within the spell's level range, and
     * each observably changes the level Iron's casts at. Anything else is refused by the editor.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_augmentMatrix_levelAugmentsWithinRangeOnly(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        var validator = com.hollingsworth.arsnouveau.api.ArsNouveauAPI.getInstance().getSpellCraftingSpellValidator();
        var amplify = com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify.INSTANCE;
        var dampen = com.hollingsworth.arsnouveau.common.spell.augment.AugmentDampen.INSTANCE;
        var extend = com.hollingsworth.arsnouveau.common.spell.augment.AugmentExtendTime.INSTANCE;
        helper.assertTrue(validator.validate(new Spell(MethodSelf.INSTANCE, glyph("irons_spellbooks:heal", 1), amplify).recipe).isEmpty(),
            "Amplify on Heal I is allowed");
        helper.assertTrue(!validator.validate(new Spell(MethodSelf.INSTANCE, glyph("irons_spellbooks:heal", 1), dampen).recipe).isEmpty(),
            "Dampen below level 1 is refused");
        int max = IronsGlyphTestSupport.maxLevel("irons_spellbooks:heal");
        helper.assertTrue(!validator.validate(new Spell(MethodSelf.INSTANCE, glyph("irons_spellbooks:heal", max), amplify).recipe).isEmpty(),
            "Amplify above the spell's maximum is refused");
        helper.assertTrue(!validator.validate(new Spell(MethodSelf.INSTANCE, glyph("irons_spellbooks:heal", 1), extend).recipe).isEmpty(),
            "an augment the glyph does not advertise is refused");
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_augments");
        IronsGlyphTestSupport.clearCooldowns(player);
        int amplified = IronsGlyphTestSupport.castLevelOf(player, "irons_spellbooks:heal",
            () -> cast(helper, player, new Spell(MethodSelf.INSTANCE, glyph("irons_spellbooks:heal", 1), amplify)));
        helper.assertTrue(amplified == 2, "Amplify must make Iron's cast Heal I at level 2, cast at " + amplified);
        IronsGlyphTestSupport.clearCooldowns(player);
        int dampened = IronsGlyphTestSupport.castLevelOf(player, "irons_spellbooks:heal",
            () -> cast(helper, player, new Spell(MethodSelf.INSTANCE, glyph("irons_spellbooks:heal", 3), dampen)));
        helper.assertTrue(dampened == 2, "Dampen must make Iron's cast Heal III at level 2, cast at " + dampened);
        helper.succeed();
    }

    /**
     * A shared spell grants nothing: the same book casts for the player who learned the spell in
     * Iron's, and is refused before payment for one who did not, who learns nothing from it.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_sharedBook_unlearnedPlayerIsRefusedAndLearnsNothing(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        String shroud = "irons_spellbooks:abyssal_shroud";
        net.minecraft.world.effect.MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(shroud));
        helper.assertTrue(effect != null, "Iron's registers the Abyssal Shroud effect");
        Spell spell = new Spell(MethodSelf.INSTANCE, glyph(shroud, 1));
        ServerPlayer owner = TouchRegressionGameTests.aimingPlayer(helper, "glyph_share_owner");
        ServerPlayer other = TouchRegressionGameTests.aimingPlayer(helper, "glyph_share_other");
        for (ServerPlayer player : new ServerPlayer[]{owner, other}) {
            player.setGameMode(GameType.SURVIVAL);
            TouchRegressionGameTests.fundArs(player);
            // A Legendary spell costs more than Iron's default 100 max mana, to which Iron's clamps the pool.
            IronsGlyphTestSupport.fundIronsPool(player, 5000.0f);
            IronsGlyphTestSupport.clearCooldowns(player);
            player.removeEffect(effect);
        }
        IronsGlyphTestSupport.learn(owner, shroud);
        cast(helper, owner, spell);
        helper.assertTrue(owner.hasEffect(effect), "the player who learned it in Iron's casts it");
        double before = TouchRegressionGameTests.arsMana(other);
        cast(helper, other, spell);
        helper.assertTrue(!other.hasEffect(effect), "the same spell is refused for a player who has not learned it");
        helper.assertTrue(TouchRegressionGameTests.arsMana(other) == before, "and the refusal costs nothing");
        IPlayerCap cap = CapabilityRegistry.getPlayerDataCap(other).orElseThrow(IllegalStateException::new);
        helper.assertTrue(!cap.knowsGlyph(glyph(shroud, 1)) && !IronsGlyphTestSupport.learned(other, shroud),
            "casting a shared spell grants neither the Ars glyph nor the Iron's spell");
        helper.succeed();
    }

    // ---- Movement and summon families -------------------------------------------------------

    /**
     * Movement goes to the Ars target, not along the caster's look: the caster turns to the sky
     * while the projectile is in flight, and Iron's Teleport still sends them where it landed.
     */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void ironsLoaded_projectileTeleport_sendsTheCasterWhereTheProjectileLanded(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_proj_tp");
        net.minecraft.core.BlockPos dirt = TouchRegressionGameTests.dirtInFront(helper);
        Vec3 target = helper.absoluteVec(Vec3.atCenterOf(dirt));
        TouchRegressionGameTests.aimAt(player, target);
        IronsGlyphTestSupport.clearCooldowns(player);
        Vec3 start = player.position();
        Vec3[] sent = new Vec3[1];
        Object watch = IronsGlyphTestSupport.watchTeleports(player, sent);
        cast(helper, player, new Spell(com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile.INSTANCE,
            glyph("irons_spellbooks:teleport", 1)));
        TouchRegressionGameTests.lookAtSky(player);
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(sent[0] != null, "Iron's Teleport must run where the projectile lands"))
            .thenExecute(() -> {
                IronsGlyphTestSupport.stopWatching(watch);
                helper.assertTrue(sent[0].distanceTo(start) > 0.5, "the destination must not be where the caster stands: " + sent[0]);
                helper.assertTrue(sent[0].distanceTo(target) < 2.5,
                    "Iron's must send the caster to where the projectile landed, not along their look: " + sent[0] + " vs " + target);
                helper.assertTrue(helper.getBlockState(dirt).is(net.minecraft.world.level.block.Blocks.DIRT), "the block is not affected");
            })
            .thenSucceed();
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_touchSummonVex_summonsAtTheTargetOwnedByTheCaster(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_touch_vex");
        net.minecraft.core.BlockPos dirt = TouchRegressionGameTests.dirtInFront(helper);
        Vec3 target = helper.absoluteVec(Vec3.atCenterOf(dirt));
        TouchRegressionGameTests.aimAt(player, target);
        IronsGlyphTestSupport.clearCooldowns(player);
        AABB around = new AABB(net.minecraft.core.BlockPos.containing(target)).inflate(4);
        cast(helper, player, new Spell(MethodTouch.INSTANCE, glyph("irons_spellbooks:summon_vex", 1)));
        helper.assertTrue(IronsGlyphTestSupport.casting(player), "Summon Vex is a long cast: it charges in Iron's own slot");
        IronsProxyCastDriver.tickUntilCastEnds(player);
        helper.succeedWhen(() -> helper.assertTrue(IronsGlyphTestSupport.summonsOwnedBy(player, around) >= 1,
            "the vexes must appear by the target Touch hit, recorded by Iron's as the caster's summons"));
    }

    // ---- Native casting state and nesting --------------------------------------------------

    /**
     * A native Iron's cast in progress is never cancelled by an imported glyph. Iron's itself
     * refuses item use while it charges, so the glyph reaches the slot through a delayed Ars
     * spell: an instant glyph resolves beside the charge, a charged one is refused while the
     * native cast holds the slot.
     */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_nativeCastInProgress_isNeverCancelledByAnImportedGlyph(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_native_slot");
        Pig pig = TouchRegressionGameTests.pigInFront(helper);
        TouchRegressionGameTests.aimAt(player, pig.getBoundingBox().getCenter());
        IronsGlyphTestSupport.clearCooldowns(player);
        cast(helper, player, new Spell(MethodSelf.INSTANCE, com.hollingsworth.arsnouveau.common.spell.effect.EffectDelay.INSTANCE,
            glyph("irons_spellbooks:heal", 1)));
        cast(helper, player, new Spell(MethodTouch.INSTANCE, com.hollingsworth.arsnouveau.common.spell.effect.EffectDelay.INSTANCE,
            glyph("irons_spellbooks:slow", 1)));
        helper.assertTrue(player.getHealth() == 10.0f, "the delayed Heal has not resolved yet");
        ItemStack ironsBook = IronsProxyCastDriver.nativeBook("irons_spellbooks:fireball");
        helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(player, ironsBook, 0)
            && "irons_spellbooks:fireball".equals(IronsGlyphTestSupport.castingSpell(player)), "a native Fireball must be charging");
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(player.getHealth() > 10.0f, "the delayed instant Heal resolves during the charge"))
            .thenExecuteAfter(20, () -> {
                helper.assertTrue("irons_spellbooks:fireball".equals(IronsGlyphTestSupport.castingSpell(player)),
                    "the native Fireball is still charging: neither glyph took over or cancelled the slot");
                helper.assertTrue(!IronsGlyphTestSupport.slowed(pig), "the charged Slow was refused while the slot was busy");
                IronsProxyCastDriver.tickUntilCastEnds(player);
                helper.assertTrue(GlyphSessions.activeFor(player.getUUID()) == 0, "both sessions close");
            })
            .thenSucceed();
    }

    /** An imported glyph inside an Ars spell exported to an Iron's scroll runs once, with its own cooldown. */
    @GameTest(template = "platform")
    public static void ironsLoaded_nested_importedGlyphInAnExportedScrollRunsOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_nested");
        player.setGameMode(GameType.SURVIVAL);
        TouchRegressionGameTests.fundArs(player);
        IronsGlyphTestSupport.clearCooldowns(player);
        Spell spell = new Spell(MethodSelf.INSTANCE, glyph("irons_spellbooks:heal", 1));
        ItemStack carrier = com.otectus.arsnspells.spell.ArsSpellExportUtil.createIronsScrollCarrier(spell, "Nested", "arcane", "spell/heal", "");
        helper.assertTrue(!carrier.isEmpty(), "the Ars spell must export to an Iron's scroll");
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        java.util.List<String> events = IronsProxyCastDriver.onCastEvents(player, () ->
            com.otectus.arsnspells.spell.CrossCastingHandler.serverHandleCast(player, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                com.otectus.arsnspells.network.CrossCastRequestPacket.Action.CAST, java.util.UUID.randomUUID()));
        helper.assertTrue(player.getHealth() > 10.0f, "the exported spell's imported Heal must resolve");
        long heals = events.stream().filter(event -> event.startsWith("irons_spellbooks:heal")).count();
        helper.assertTrue(heals == 1, "Iron's must see exactly one Heal cast for one scroll use, saw " + events);
        helper.assertTrue(IronsGlyphTestSupport.onCooldown(player, "irons_spellbooks:heal"), "the nested Heal starts its own cooldown");
        helper.assertTrue(GlyphSessions.activeFor(player.getUUID()) == 0, "the nested session closes");
        helper.succeed();
    }

    // ---- Fixtures --------------------------------------------------------------------------

    static ImportedIronEffect glyph(String spell, int level) {
        return ImportedGlyphRegistrar.part(new ImportedGlyphKey(new ResourceLocation(spell), level))
            .orElseThrow(() -> new IllegalStateException("no imported glyph for " + spell + " level " + level));
    }

    /** Cast {@code spell} from an archmage book through the server's item-use path. */
    static void cast(GameTestHelper helper, ServerPlayer player, Spell spell) {
        ItemStack book = TouchRegressionGameTests.nativeBook(spell);
        player.setItemInHand(InteractionHand.MAIN_HAND, book);
        player.gameMode.useItem(player, helper.getLevel(), book, InteractionHand.MAIN_HAND);
    }

    static Vec3 eye(ServerPlayer player) {
        return player.getEyePosition();
    }
}
