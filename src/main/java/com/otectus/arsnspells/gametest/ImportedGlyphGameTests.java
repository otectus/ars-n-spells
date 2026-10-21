package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.registry.SpellCasterRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.bridge.NativeManaAccess;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistration;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.runtime.GlyphOutcome;
import com.otectus.arsnspells.glyph.runtime.GlyphSessions;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Imported Iron's glyphs resolving inside real Ars spells: one representative per adapter family,
 * cast through Ars's own spellbook, with the world effect, the payment and the native cooldown as
 * the observables.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class ImportedGlyphGameTests {

    static final Vec3 STAND = new Vec3(1.5, 2.0, 2.5);
    static final Vec3 TARGET = new Vec3(3.5, 2.0, 2.5);
    static final BlockPos FRONT = new BlockPos(3, 3, 2);

    private ImportedGlyphGameTests() {
    }

    // ---- Families ----

    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_selfHeal_healsTheCaster(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_self_heal", GameType.CREATIVE);
        player.setHealth(6.0f);
        cast(player, spell(MethodSelf.INSTANCE, glyph("heal", 3)));
        helper.assertTrue(player.getHealth() > 6.0f, "Self -> Iron Heal III must heal its caster; health " + player.getHealth());
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_glyph_runtime", timeoutTicks = 60)
    public static void ironsLoaded_touchFirebolt_hitsTheTouchedTarget(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_touch_bolt", GameType.CREATIVE);
        Pig pig = pig(helper);
        float before = pig.getHealth();
        aim(player, pig.getBoundingBox().getCenter());
        cast(player, spell(MethodTouch.INSTANCE, glyph("firebolt", 3)));
        helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() < before || pig.isOnFire(),
            "Touch -> Iron Firebolt must strike the touched pig with a native firebolt; health " + pig.getHealth()));
    }

    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_selfHeal_paysIronsLegAndStartsNativeCooldownBySpellId(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_heal_pay", GameType.SURVIVAL);
        fund(player);
        player.setHealth(6.0f);
        double before = pools(player);
        cast(player, spell(MethodSelf.INSTANCE, glyph("heal", 3)));
        helper.assertTrue(player.getHealth() > 6.0f, "the funded survival cast must heal");
        helper.assertTrue(pools(player) < before, "the cast must be paid: " + before + " -> " + pools(player));
        helper.assertTrue(IronsProxyCastDriver.onNativeCooldown(player, "irons_spellbooks:heal"),
            "the imported glyph must start Iron's own cooldown for irons_spellbooks:heal");
        float healthAfterFirst = player.getHealth();
        player.setHealth(6.0f);
        cast(player, spell(MethodSelf.INSTANCE, glyph("heal", 1)));
        helper.assertTrue(player.getHealth() == 6.0f, "Heal I is the same Iron's spell: the native cooldown must refuse it ("
            + healthAfterFirst + " then " + player.getHealth() + ")");
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_glyph_runtime", timeoutTicks = 80)
    public static void ironsLoaded_touchSlow_slowsTheTouchedEntity(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_touch_slow", GameType.CREATIVE);
        Pig pig = pig(helper);
        aim(player, pig.getBoundingBox().getCenter());
        cast(player, spell(MethodTouch.INSTANCE, glyph("slow", 1)));
        helper.assertTrue(!hasEffect(pig, "irons_spellbooks:slowed"), "Slow is a charged spell: nothing may happen before its cast time");
        helper.succeedWhen(() -> helper.assertTrue(hasEffect(pig, "irons_spellbooks:slowed"),
            "after its charge, Touch -> Iron Slow must slow the touched pig, not the caster"));
    }

    @GameTest(template = "platform", batch = "ans_glyph_runtime", timeoutTicks = 40)
    public static void ironsLoaded_touchLightningBolt_strikesTheTouchedPoint(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_touch_bolt2", GameType.CREATIVE);
        Pig pig = pig(helper);
        float before = pig.getHealth();
        aim(player, pig.getBoundingBox().getCenter());
        cast(player, spell(MethodTouch.INSTANCE, glyph("lightning_bolt", 1)));
        helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() < before,
            "Touch -> Iron Lightning Bolt must strike at the touched pig; health " + pig.getHealth()));
    }

    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_touchTouchDig_breaksTheTouchedBlock(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_touch_dig", GameType.SURVIVAL);
        fund(player);
        helper.setBlock(FRONT, Blocks.DIRT);
        aim(player, Vec3.atCenterOf(helper.absolutePos(FRONT)));
        cast(player, spell(MethodTouch.INSTANCE, glyph("touch_dig", 1)));
        helper.assertBlockPresent(Blocks.AIR, FRONT);
        helper.succeed();
    }

    /**
     * Touch -> Iron Teleport lands the caster on the touched floor and moves nothing else. A
     * FakePlayer's network handler drops teleports, so the evidence is the teleport Iron's own
     * {@code TeleportSpell} requested (its {@code SpellTeleportEvent}) rather than the caster's position.
     */
    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_touchTeleport_movesOnlyTheCaster(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        IronsProxyCastDriver.recordTeleports();
        ServerPlayer player = caster(helper, "glyph_touch_tp", GameType.CREATIVE);
        Pig bystander = helper.spawn(EntityType.PIG, new Vec3(1.5, 2.0, 0.5));
        bystander.setNoAi(true);
        Vec3 bystanderBefore = bystander.position();
        IronsProxyCastDriver.forgetTeleport(player.getUUID());
        IronsProxyCastDriver.forgetTeleport(bystander.getUUID());
        Vec3 landing = helper.absoluteVec(new Vec3(3.5, 2.0, 2.5));
        aim(player, landing);
        cast(player, spell(MethodTouch.INSTANCE, glyph("teleport", 1)));
        Vec3 requested = IronsProxyCastDriver.lastTeleport(player.getUUID());
        helper.assertTrue(requested != null, "Touch -> Iron Teleport must run Iron's own teleport for the caster");
        helper.assertTrue(requested.distanceTo(landing) < 1.0,
            "the caster must be sent to the touched floor: requested " + requested + ", touched " + landing);
        helper.assertTrue(IronsProxyCastDriver.lastTeleport(bystander.getUUID()) == null && bystander.position().equals(bystanderBefore),
            "a teleport glyph moves only its caster");
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_glyph_runtime", timeoutTicks = 60)
    public static void ironsLoaded_touchSummonVex_summonsAtTheTarget(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_touch_vex", GameType.CREATIVE);
        Vec3 spot = helper.absoluteVec(new Vec3(3.5, 2.0, 2.5));
        aim(player, spot);
        cast(player, spell(MethodTouch.INSTANCE, glyph("summon_vex", 1)));
        helper.succeedWhen(() -> {
            var vexes = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(spot, spot).inflate(3.0),
                e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals("irons_spellbooks:summoned_vex"));
            helper.assertTrue(!vexes.isEmpty(), "Touch -> Iron Summon Vex must summon vexes at the touched spot");
        });
    }

    @GameTest(template = "platform", batch = "ans_glyph_runtime", timeoutTicks = 200)
    public static void ironsLoaded_selfConeOfCold_channelsAndPaysPerPulse(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_self_cone", GameType.SURVIVAL);
        fund(player);
        double before = pools(player);
        cast(player, spell(MethodSelf.INSTANCE, glyph("cone_of_cold", 1)));
        double afterStart = pools(player);
        helper.assertTrue(afterStart < before, "the first pulse must be paid when the channel starts");
        helper.assertTrue(!IronsProxyCastDriver.castingSpell(player, "irons_spellbooks:cone_of_cold"),
            "the channel runs on a detached slot: the player's own Iron's casting slot stays free");
        helper.succeedWhen(() -> {
            var outcomes = GlyphSessions.outcomes(player.getUUID());
            helper.assertTrue(outcomes.stream().anyMatch(o -> o.outcome() == GlyphOutcome.COMPLETED),
                "the channel must complete: " + outcomes);
            helper.assertTrue(pools(player) < afterStart, "later pulses must each be paid: " + afterStart + " -> " + pools(player));
            helper.assertTrue(IronsProxyCastDriver.onNativeCooldown(player, "irons_spellbooks:cone_of_cold"),
                "a completed channel commits Iron's own cooldown");
        });
    }

    /** Damage interrupts a charge, as Iron's interrupts a long cast; the held price is returned. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime", timeoutTicks = 80)
    public static void ironsLoaded_chargeInterruptedByDamage_releasesItsReservation(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_interrupt", GameType.SURVIVAL);
        fund(player);
        Pig pig = pig(helper);
        aim(player, pig.getBoundingBox().getCenter());
        double before = pools(player);
        cast(player, spell(MethodTouch.INSTANCE, glyph("fireball", 1)));
        double charging = pools(player);
        helper.assertTrue(charging < before, "the charge holds its price while charging");
        // A FakePlayer is invulnerable, so hurt() returns before NeoForge's damage hook; post exactly
        // what LivingEntity.hurt posts for a real player taking this hit.
        net.neoforged.neoforge.common.CommonHooks.onEntityIncomingDamage(player,
            new net.neoforged.neoforge.common.damagesource.DamageContainer(player.damageSources().generic(), 1.0f));
        helper.runAfterDelay(50, () -> {
            var outcomes = GlyphSessions.outcomes(player.getUUID());
            helper.assertTrue(outcomes.stream().anyMatch(o -> o.outcome() == GlyphOutcome.CANCELED),
                "damage must interrupt the charge: " + outcomes);
            helper.assertTrue(outcomes.stream().noneMatch(o -> o.outcome() == GlyphOutcome.EFFECT_APPLIED),
                "an interrupted charge never fires");
            double ironsHeld = before - pools(player);
            helper.assertTrue(pools(player) > charging, "the held Iron's price must be returned (" + charging + " -> " + pools(player)
                + "; Ars leg " + ironsHeld + " stays paid)");
            helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the cancelled charge leaves no open session");
            helper.succeed();
        });
    }

    /**
     * A session that reaches its lifetime bound (1,800 ticks; brought forward here) is closed by the
     * sweep: its held price is returned, the glyph never fires and nothing of it stays open.
     */
    @GameTest(template = "platform", batch = "ans_glyph_runtime", timeoutTicks = 120)
    public static void ironsLoaded_expiredSession_closesAndReturnsItsHold(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_expire", GameType.SURVIVAL);
        fund(player);
        Pig pig = pig(helper);
        aim(player, pig.getBoundingBox().getCenter());
        double before = pools(player);
        cast(player, spell(MethodTouch.INSTANCE, glyph("fireball", 1)));
        double charging = pools(player);
        helper.assertTrue(charging < before && !GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the charge holds its price in an open session");
        GlyphSessions.expireForTesting(player.getUUID());
        helper.runAfterDelay(25, () -> {
            var outcomes = GlyphSessions.outcomes(player.getUUID());
            helper.assertTrue(outcomes.stream().anyMatch(o -> o.outcome() == GlyphOutcome.CANCELED && o.detail().contains("lifetime bound")),
                "the sweep closes the session at its bound: " + outcomes);
            helper.assertTrue(outcomes.stream().noneMatch(o -> o.outcome() == GlyphOutcome.EFFECT_APPLIED), "an expired charge never fires");
            helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "nothing of the session stays open");
            helper.assertTrue(pools(player) > charging, "the held Iron's price is returned: " + charging + " -> " + pools(player));
            helper.succeed();
        });
    }

    /** Eldritch spells need learning in Iron's: the same spell refuses an unlearned caster and works for a learned one. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_eldritchGlyph_requiresIronsLearningPerCaster(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer unlearned = caster(helper, "glyph_unlearned", GameType.CREATIVE);
        cast(unlearned, spell(MethodSelf.INSTANCE, glyph("abyssal_shroud", 1)));
        helper.assertTrue(!hasEffect(unlearned, "irons_spellbooks:abyssal_shroud"),
            "a caster who has not learned Abyssal Shroud in Iron's must be refused, even holding a book with the glyph");
        ServerPlayer learned = caster(helper, "glyph_learned", GameType.CREATIVE);
        IronsProxyCastDriver.learnSpell(learned, "irons_spellbooks:abyssal_shroud");
        cast(learned, spell(MethodSelf.INSTANCE, glyph("abyssal_shroud", 1)));
        helper.assertTrue(hasEffect(learned, "irons_spellbooks:abyssal_shroud"), "a caster who learned it gets the native effect");
        helper.succeed();
    }

    /** Native recast stages are shared with Iron's: the first use opens them, a later use spends one, no cooldown yet. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_recastSpell_followsTheNativeStageMachine(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_recast", GameType.SURVIVAL);
        fund(player);
        IronsProxyCastDriver.learnSpell(player, "irons_spellbooks:eldritch_blast");
        Pig pig = pig(helper);
        aim(player, pig.getBoundingBox().getCenter());
        cast(player, spell(MethodTouch.INSTANCE, glyph("eldritch_blast", 1)));
        int afterFirst = IronsProxyCastDriver.remainingRecasts(player, "irons_spellbooks:eldritch_blast");
        helper.assertTrue(afterFirst > 0, "the first blast must open Iron's recast window");
        helper.assertTrue(!IronsProxyCastDriver.onNativeCooldown(player, "irons_spellbooks:eldritch_blast"),
            "no cooldown while recasts remain");
        cast(player, spell(MethodTouch.INSTANCE, glyph("eldritch_blast", 1)));
        helper.assertTrue(IronsProxyCastDriver.remainingRecasts(player, "irons_spellbooks:eldritch_blast") == afterFirst - 1,
            "a later use spends one native recast");
        helper.succeed();
    }

    /**
     * The first use of a recast spell pays its glyph price; a native recast is free on the Iron's
     * side, as in Iron's itself. The Ars leg (Touch) is the same for both casts, so the difference
     * between the two spends is exactly the quoted glyph price.
     */
    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_recastSpell_paysOnlyTheFirstUse(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_recast_pay", GameType.SURVIVAL);
        fund(player);
        IronsProxyCastDriver.learnSpell(player, "irons_spellbooks:eldritch_blast");
        Pig pig = pig(helper);
        aim(player, pig.getBoundingBox().getCenter());
        double glyphPrice = glyphPrice(player, "eldritch_blast", 1);
        double start = pools(player);
        cast(player, spell(MethodTouch.INSTANCE, glyph("eldritch_blast", 1)));
        double first = start - pools(player);
        double beforeRecast = pools(player);
        cast(player, spell(MethodTouch.INSTANCE, glyph("eldritch_blast", 1)));
        double recast = beforeRecast - pools(player);
        helper.assertTrue(glyphPrice > 0.0, "eldritch blast has a glyph price");
        helper.assertTrue(Math.abs((first - recast) - glyphPrice) < 0.01,
            "first use paid " + first + ", recast " + recast + "; their difference must be the glyph price " + glyphPrice);
        helper.succeed();
    }

    /** What the runtime quotes for the Iron's leg of {@code spell} at {@code level}, in pool units. */
    static double glyphPrice(ServerPlayer player, String spell, int level) {
        var quote = com.otectus.arsnspells.glyph.runtime.GlyphPayments.quote(player,
            com.otectus.arsnspells.glyph.IronGlyphBridges.get().manaCost(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", spell), level),
            com.otectus.arsnspells.casting.QuoteService.currentRules());
        return quote.total(ResourceUnit.ARS_MANA) + quote.total(ResourceUnit.IRONS_MANA);
    }

    /** A cast with imported glyphs from anything but a player is refused before it resolves or costs anything. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_nonPlayerCaster_isRefusedBeforeEffects(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawn(EntityType.ZOMBIE, TARGET);
        zombie.setNoAi(true);
        zombie.setHealth(5.0f);
        var context = new com.hollingsworth.arsnouveau.api.spell.SpellContext(helper.getLevel(),
            spell(MethodSelf.INSTANCE, glyph("heal", 1)), zombie,
            com.hollingsworth.arsnouveau.api.spell.wrapped_caster.LivingCaster.from(zombie));
        boolean cast = new com.hollingsworth.arsnouveau.api.spell.SpellResolver(context).onCast(ItemStack.EMPTY, helper.getLevel());
        helper.assertTrue(!cast, "Ars must not report the cast as made");
        helper.assertTrue(zombie.getHealth() == 5.0f, "a non-player caster must not get the imported effect");
        helper.succeed();
    }

    // ---- Delivery, ordering and ownership ----

    /** A delivery that misses releases the glyph's held price; the launched Ars leg stays paid. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime", timeoutTicks = 60)
    public static void ironsLoaded_projectileMiss_releasesTheGlyphHoldButKeepsTheArsLeg(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_proj_miss", GameType.SURVIVAL);
        fund(player);
        wall(helper, Blocks.STONE);
        aim(player, helper.absoluteVec(new Vec3(4.0, 3.5, 2.5)));
        Spell spell = spell(MethodProjectile.INSTANCE, glyph("slow", 1));
        double arsLeg = ImportedGlyphPaymentGameTests.quoteTotal(com.otectus.arsnspells.casting.QuoteService.quote(player,
            ResourceUnit.ARS_MANA, spell.getCost(), com.otectus.arsnspells.casting.QuoteService.currentRules(),
            com.otectus.arsnspells.contract.CarrierPolicy.NATIVE_ONLY));
        double glyphLeg = ImportedGlyphPaymentGameTests.glyphQuote(player, ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "slow"), 1);
        double start = pools(player);
        GlyphSessions.clearOutcomes(player.getUUID());
        cast(player, spell);
        helper.assertTrue(Math.abs((start - pools(player)) - (arsLeg + glyphLeg)) < 0.02,
            "in flight, the Ars leg is paid and the glyph's first execution is held: " + (start - pools(player)));
        helper.succeedWhen(() -> {
            helper.assertTrue(GlyphSessions.outcomes(player.getUUID()).stream().anyMatch(o -> o.outcome() == GlyphOutcome.REJECTED
                && o.detail().contains("validation.living_target")), "the stone wall is no living target: " + GlyphSessions.outcomes(player.getUUID()));
            helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the session closes with its delivery");
            helper.assertTrue(Math.abs((start - pools(player)) - arsLeg) < 0.02,
                "after the miss only the Ars leg stays paid: spent " + (start - pools(player)) + ", Ars leg " + arsLeg);
        });
    }

    /** Split deliveries are separate executions: each one that resolves is authorized and paid. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime", timeoutTicks = 60)
    public static void ironsLoaded_projectileSplit_paysEveryExecution(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_proj_split", GameType.SURVIVAL);
        fund(player);
        wall(helper, Blocks.DIRT);
        aim(player, helper.absoluteVec(new Vec3(3.0, 3.5, 2.5)));
        Spell spell = spell(MethodProjectile.INSTANCE, com.hollingsworth.arsnouveau.common.spell.augment.AugmentSplit.INSTANCE,
            com.hollingsworth.arsnouveau.common.spell.augment.AugmentSplit.INSTANCE, glyph("touch_dig", 1));
        double arsLeg = ImportedGlyphPaymentGameTests.quoteTotal(com.otectus.arsnspells.casting.QuoteService.quote(player,
            ResourceUnit.ARS_MANA, spell.getCost(), com.otectus.arsnspells.casting.QuoteService.currentRules(),
            com.otectus.arsnspells.contract.CarrierPolicy.NATIVE_ONLY));
        double glyphLeg = ImportedGlyphPaymentGameTests.glyphQuote(player, ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "touch_dig"), 1);
        double start = pools(player);
        GlyphSessions.clearOutcomes(player.getUUID());
        cast(player, spell);
        helper.succeedWhen(() -> {
            long applied = GlyphSessions.outcomes(player.getUUID()).stream().filter(o -> o.outcome() == GlyphOutcome.EFFECT_APPLIED).count();
            helper.assertTrue(applied == 3, "Projectile + Split x2 makes three deliveries, each one execution: " + GlyphSessions.outcomes(player.getUUID()));
            helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the session closes after its last delivery");
            helper.assertTrue(Math.abs((start - pools(player)) - (arsLeg + 3 * glyphLeg)) < 0.02,
                "three executions pay three glyph prices once each: spent " + (start - pools(player)) + ", expected " + (arsLeg + 3 * glyphLeg));
        });
    }

    /**
     * Projectile -> an imported living-target effect, with the caster switching items in flight:
     * the delivery keeps its own session and authorization and slows the pig it hits.
     */
    @GameTest(template = "platform", batch = "ans_glyph_runtime", timeoutTicks = 100)
    public static void ironsLoaded_projectileImportedEffect_keepsItsSessionAfterItemSwitch(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_proj_slow", GameType.SURVIVAL);
        fund(player);
        Pig pig = pig(helper);
        aim(player, pig.getBoundingBox().getCenter());
        double glyphLeg = ImportedGlyphPaymentGameTests.glyphQuote(player, ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "slow"), 1);
        GlyphSessions.clearOutcomes(player.getUUID());
        cast(player, spell(MethodProjectile.INSTANCE, glyph("slow", 1)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.STICK));
        double afterLaunch = pools(player);
        helper.succeedWhen(() -> {
            helper.assertTrue(hasEffect(pig, "irons_spellbooks:slowed"), "the projectile must slow the pig it hits after its charge");
            helper.assertTrue(Math.abs(afterLaunch - pools(player)) < 0.02,
                "the hit runs on the session's held price, not a fresh payment from whatever is now held ("
                    + afterLaunch + " -> " + pools(player) + ", glyph " + glyphLeg + ")");
        });
    }

    /** Ars effects after a charged imported glyph wait for it, then run exactly once, in order. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime", timeoutTicks = 100)
    public static void ironsLoaded_followingArsEffectRunsOnceAfterTheCharge(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_order", GameType.SURVIVAL);
        fund(player);
        Pig pig = pig(helper);
        aim(player, pig.getBoundingBox().getCenter());
        int[] hits = {0};
        boolean[] slowedFirst = {true};
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post> counter = event -> {
            if (event.getEntity() == pig) {
                hits[0]++;
                if (!hasEffect(pig, "irons_spellbooks:slowed")) slowedFirst[0] = false;
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post.class, counter);
        cast(player, spell(MethodTouch.INSTANCE, glyph("slow", 1), com.hollingsworth.arsnouveau.common.spell.effect.EffectHarm.INSTANCE));
        helper.assertTrue(hits[0] == 0, "Harm follows the charged Slow: it must not run before the charge completes");
        helper.runAfterDelay(70, () -> {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(counter);
            helper.assertTrue(hasEffect(pig, "irons_spellbooks:slowed"), "the charged Slow applied");
            helper.assertTrue(hits[0] == 1, "the following Harm ran exactly once, got " + hits[0]);
            helper.assertTrue(slowedFirst[0], "Harm ran after Slow, in recipe order");
            helper.succeed();
        });
    }

    /** Iron's own pre-cast event can veto an imported glyph: no effect, its hold released, the Ars leg kept. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_nativePreCastVeto_refusesTheGlyphAndReleasesItsHold(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_veto", GameType.SURVIVAL);
        fund(player);
        player.setHealth(10.0f);
        Spell spell = spell(MethodSelf.INSTANCE, glyph("heal", 1));
        double arsLeg = ImportedGlyphPaymentGameTests.quoteTotal(com.otectus.arsnspells.casting.QuoteService.quote(player,
            ResourceUnit.ARS_MANA, spell.getCost(), com.otectus.arsnspells.casting.QuoteService.currentRules(),
            com.otectus.arsnspells.contract.CarrierPolicy.NATIVE_ONLY));
        double start = pools(player);
        GlyphSessions.clearOutcomes(player.getUUID());
        Runnable unveto = IronsProxyCastDriver.vetoPreCast(player, "irons_spellbooks:heal");
        try {
            cast(player, spell);
        } finally {
            unveto.run();
        }
        helper.assertTrue(player.getHealth() == 10.0f, "a vetoed Heal must not heal");
        helper.assertTrue(GlyphSessions.outcomes(player.getUUID()).stream().anyMatch(o -> o.outcome() == GlyphOutcome.REJECTED
            && o.detail().contains("refused.vetoed")), "the veto is reported: " + GlyphSessions.outcomes(player.getUUID()));
        helper.assertTrue(!IronsProxyCastDriver.onNativeCooldown(player, "irons_spellbooks:heal"), "a vetoed glyph starts no cooldown");
        helper.assertTrue(Math.abs((start - pools(player)) - arsLeg) < 0.02,
            "the launched Ars leg stays paid and the glyph's hold is released: spent " + (start - pools(player)) + ", Ars leg " + arsLeg);
        helper.succeed();
    }

    /** A channel pays each later pulse before it runs and ends cleanly on the last one the caster can afford. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime", timeoutTicks = 200)
    public static void ironsLoaded_channelStopsOnTheLastAffordablePulse(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_chan_poor", GameType.SURVIVAL);
        fund(player);
        Spell spell = spell(MethodSelf.INSTANCE, glyph("cone_of_cold", 1));
        var rules = com.otectus.arsnspells.casting.QuoteService.currentRules();
        var arsQuote = com.otectus.arsnspells.casting.QuoteService.quote(player, ResourceUnit.ARS_MANA, spell.getCost(), rules,
            com.otectus.arsnspells.contract.CarrierPolicy.NATIVE_ONLY);
        var pulseQuote = com.otectus.arsnspells.glyph.runtime.GlyphPayments.quote(player,
            com.otectus.arsnspells.glyph.IronGlyphBridges.get().manaCost(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "cone_of_cold"), 1), rules);
        helper.assertTrue(arsQuote.total(ResourceUnit.ARS_MANA) == 0 && pulseQuote.total(ResourceUnit.ARS_MANA) == 0,
            "the default mode pays both legs from the Iron's pool");
        double pulse = pulseQuote.total(ResourceUnit.IRONS_MANA);
        IronsProxyCastDriver.setIronsMana(player, (float) (arsQuote.total(ResourceUnit.IRONS_MANA) + 2.5 * pulse));
        GlyphSessions.clearOutcomes(player.getUUID());
        cast(player, spell);
        helper.succeedWhen(() -> {
            var done = GlyphSessions.outcomes(player.getUUID()).stream().filter(o -> o.outcome() == GlyphOutcome.COMPLETED).findFirst();
            helper.assertTrue(done.isPresent() && done.get().detail().equals("ran out of funds after 1 paid pulse(s)"),
                "the first pulse is prepaid, the second paid, the third unaffordable: " + GlyphSessions.outcomes(player.getUUID()));
            helper.assertTrue(Math.abs(IronsProxyCastDriver.ironsMana(player) - 0.5 * pulse) < 0.02,
                "half a pulse is left unspent: " + IronsProxyCastDriver.ironsMana(player));
            helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the session closes when the channel ends");
        });
    }

    // ---- Scaling and training ----

    /** One glyph cast: one Ars cast event, one Iron's cast event, and one cast of training for the glyph's school. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_glyphTrainsOnceAndFiresEachNativeEventOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_training", GameType.SURVIVAL);
        fund(player);
        player.setHealth(10.0f);
        String holy = "irons_spellbooks:holy";
        int castsBefore = player.getData(com.otectus.arsnspells.data.AttachmentTypes.PROGRESSION.get()).getCastCount(holy);
        int[] arsCasts = {0};
        java.util.List<String> ironsEvents = IronsProxyCastDriver.onCastEvents(player, () ->
            arsCasts[0] = SpellCastCounter.count(player.getUUID(), () -> cast(player, spell(MethodSelf.INSTANCE, glyph("heal", 1)))));
        helper.assertTrue(player.getHealth() > 10.0f, "the glyph heals");
        helper.assertTrue(arsCasts[0] == 1, "one Ars cast event for the root cast, got " + arsCasts[0]);
        helper.assertTrue(ironsEvents.size() == 1 && ironsEvents.get(0).startsWith("irons_spellbooks:heal"),
            "exactly one Iron's cast event for the imported effect: " + ironsEvents);
        int castsAfter = player.getData(com.otectus.arsnspells.data.AttachmentTypes.PROGRESSION.get()).getCastCount(holy);
        helper.assertTrue(castsAfter == castsBefore + 1, "the holy school is trained once, not once per layer: " + castsBefore + " -> " + castsAfter);
        helper.succeed();
    }

    /** The glyph's damage is Iron's own: the same as a native cast with the same caster and level, multiplied once. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime", timeoutTicks = 100)
    public static void ironsLoaded_glyphDamageEqualsTheNativeCast(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Pig nativeTarget = helper.spawn(EntityType.PIG, new Vec3(3.5, 2.0, 0.5));
        Pig glyphTarget = helper.spawn(EntityType.PIG, new Vec3(3.5, 2.0, 4.5));
        nativeTarget.setNoAi(true);
        glyphTarget.setNoAi(true);
        java.util.Map<Pig, Float> firstHit = new java.util.HashMap<>();
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post> recorder = event -> {
            if (event.getEntity() instanceof Pig pig && (pig == nativeTarget || pig == glyphTarget)) firstHit.putIfAbsent(pig, event.getNewDamage());
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post.class, recorder);
        ServerPlayer nativeCaster = caster(helper, "glyph_dmg_native", GameType.SURVIVAL);
        fund(nativeCaster);
        nativeCaster.moveTo(helper.absoluteVec(new Vec3(1.5, 2.0, 0.5)));
        aim(nativeCaster, nativeTarget.getBoundingBox().getCenter());
        ItemStack book = IronsProxyCastDriver.nativeBook("irons_spellbooks:firebolt");
        helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(nativeCaster, book, 0), "the native firebolt must cast");
        // Iron's finishes a cast on its per-player tick, which skips fake players: run it as a real player's tick would.
        IronsProxyCastDriver.tickUntilCastEnds(nativeCaster);
        ServerPlayer glyphCaster = caster(helper, "glyph_dmg_glyph", GameType.SURVIVAL);
        fund(glyphCaster);
        glyphCaster.moveTo(helper.absoluteVec(new Vec3(1.5, 2.0, 4.5)));
        aim(glyphCaster, glyphTarget.getBoundingBox().getCenter());
        cast(glyphCaster, spell(MethodTouch.INSTANCE, glyph("firebolt", 1)));
        helper.succeedWhen(() -> {
            helper.assertTrue(firstHit.containsKey(nativeTarget) && firstHit.containsKey(glyphTarget), "both firebolts must land: " + firstHit);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(recorder);
            float nativeDamage = firstHit.get(nativeTarget);
            float glyphDamage = firstHit.get(glyphTarget);
            helper.assertTrue(Math.abs(nativeDamage - glyphDamage) < 0.01f,
                "a glyph firebolt deals what a native firebolt deals: native " + nativeDamage + ", glyph " + glyphDamage);
        });
    }

    /** Turrets, runes and other block casters have no supported glyph route: refused before effects or cost. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_turretCaster_isRefusedBeforeEffects(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        BlockPos turretPos = new BlockPos(1, 2, 1);
        helper.setBlock(turretPos, com.hollingsworth.arsnouveau.setup.registry.BlockRegistry.BASIC_SPELL_TURRET.get());
        var tile = helper.getBlockEntity(turretPos);
        helper.assertTrue(tile != null, "the turret placed");
        var fake = com.hollingsworth.arsnouveau.api.ANFakePlayer.getPlayer(helper.getLevel());
        fake.setHealth(5.0f);
        var context = new com.hollingsworth.arsnouveau.api.spell.SpellContext(helper.getLevel(),
            spell(MethodSelf.INSTANCE, glyph("heal", 1)), fake,
            new com.hollingsworth.arsnouveau.api.spell.wrapped_caster.TileCaster(tile, com.hollingsworth.arsnouveau.api.spell.SpellContext.CasterType.TURRET));
        boolean cast = new com.hollingsworth.arsnouveau.api.spell.SpellResolver(context).onCast(ItemStack.EMPTY, helper.getLevel());
        helper.assertTrue(!cast && fake.getHealth() == 5.0f, "a turret's cast with an imported glyph is refused, never a free fake-player cast");
        helper.succeed();
    }

    /** Ars's own book-tier rule applies to imported glyphs through their behaviour tier. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_bookTierGatesImportedGlyphs(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ImportedIronEffect common = glyph("firebolt", 1);
        ImportedIronEffect legendary = glyph("firebolt", 10);
        helper.assertTrue(common.getConfigTier().value == 1 && legendary.getConfigTier().value == 3,
            "Firebolt I is tier I and Firebolt X tier III: " + common.getConfigTier().value + ", " + legendary.getConfigTier().value);
        var novice = new com.hollingsworth.arsnouveau.common.spell.validation.GlyphMaxTierValidator(1);
        helper.assertTrue(novice.validate(java.util.List.of(MethodTouch.INSTANCE, common)).isEmpty(), "a novice book takes a tier I glyph");
        helper.assertTrue(!novice.validate(java.util.List.of(MethodTouch.INSTANCE, legendary)).isEmpty(), "but refuses a tier III glyph");
        helper.succeed();
    }

    // ---- Augments do something observable ----

    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_amplifyAndDampenChangeTheEffectiveLevel(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        var amplify = com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify.INSTANCE;
        var dampen = com.hollingsworth.arsnouveau.common.spell.augment.AugmentDampen.INSTANCE;
        float plainOne = healed(helper, "glyph_amp_i", spell(MethodSelf.INSTANCE, glyph("heal", 1)));
        float amplified = healed(helper, "glyph_amp_ii", spell(MethodSelf.INSTANCE, glyph("heal", 1), amplify));
        float plainThree = healed(helper, "glyph_damp_iii", spell(MethodSelf.INSTANCE, glyph("heal", 3)));
        float dampened = healed(helper, "glyph_damp_ii", spell(MethodSelf.INSTANCE, glyph("heal", 3), dampen));
        helper.assertTrue(amplified > plainOne, "Amplify must raise Heal I's effective level: " + plainOne + " vs " + amplified);
        helper.assertTrue(dampened < plainThree, "Dampen must lower Heal III's effective level: " + plainThree + " vs " + dampened);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_glyph_runtime", timeoutTicks = 80)
    public static void ironsLoaded_accelerateAndDecelerateChangeTheChargeTime(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ResourceLocation slow = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "slow");
        ServerPlayer quick = caster(helper, "glyph_accel", GameType.SURVIVAL);
        fund(quick);
        int nativeTicks = com.otectus.arsnspells.glyph.IronGlyphBridges.get().effectiveCastTime(slow, 1, quick);
        Pig first = pig(helper);
        aim(quick, first.getBoundingBox().getCenter());
        GlyphSessions.clearOutcomes(quick.getUUID());
        cast(quick, spell(MethodTouch.INSTANCE, glyph("slow", 1), com.hollingsworth.arsnouveau.common.spell.augment.AugmentAccelerate.INSTANCE));
        ServerPlayer slowCaster = caster(helper, "glyph_decel", GameType.SURVIVAL);
        fund(slowCaster);
        slowCaster.moveTo(helper.absoluteVec(new Vec3(1.5, 2.0, 0.5)));
        Pig second = helper.spawn(EntityType.PIG, new Vec3(3.5, 2.0, 0.5));
        second.setNoAi(true);
        aim(slowCaster, second.getBoundingBox().getCenter());
        GlyphSessions.clearOutcomes(slowCaster.getUUID());
        cast(slowCaster, spell(MethodTouch.INSTANCE, glyph("slow", 1), com.hollingsworth.arsnouveau.common.spell.augment.AugmentDecelerate.INSTANCE));
        int fast = (int) Math.round(nativeTicks * 0.75);
        int slower = (int) Math.round(nativeTicks * 1.25);
        helper.assertTrue(GlyphSessions.outcomes(quick.getUUID()).stream().anyMatch(o -> o.detail().equals("charge " + fast + " ticks")),
            "Accelerate shortens the " + nativeTicks + "-tick charge to " + fast + ": " + GlyphSessions.outcomes(quick.getUUID()));
        helper.assertTrue(GlyphSessions.outcomes(slowCaster.getUUID()).stream().anyMatch(o -> o.detail().equals("charge " + slower + " ticks")),
            "Decelerate lengthens it to " + slower + ": " + GlyphSessions.outcomes(slowCaster.getUUID()));
        helper.runAfterDelay(fast + 2, () -> {
            helper.assertTrue(hasEffect(first, "irons_spellbooks:slowed"), "the accelerated Slow lands before the native cast time");
            helper.assertTrue(!hasEffect(second, "irons_spellbooks:slowed"), "the decelerated Slow is still charging");
        });
        helper.runAfterDelay(slower + 3, () -> {
            helper.assertTrue(hasEffect(second, "irons_spellbooks:slowed"), "the decelerated Slow lands after its longer charge");
            helper.succeed();
        });
    }

    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_accelerateSpeedsAnImportedProjectile(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        double plain = launchSpeed(helper, "glyph_bolt_plain", spell(MethodSelf.INSTANCE, glyph("firebolt", 1)));
        double fast = launchSpeed(helper, "glyph_bolt_fast", spell(MethodSelf.INSTANCE, glyph("firebolt", 1),
            com.hollingsworth.arsnouveau.common.spell.augment.AugmentAccelerate.INSTANCE));
        helper.assertTrue(Math.abs(fast / plain - 1.25) < 0.01, "Accelerate launches the native firebolt 25% faster: " + plain + " vs " + fast);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_glyph_runtime", timeoutTicks = 260)
    public static void ironsLoaded_extendTimeAndDurationDownChangeTheChannel(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer base = channeller(helper, "glyph_chan_base", 0.5, spell(MethodSelf.INSTANCE, glyph("cone_of_cold", 1)));
        ServerPlayer longer = channeller(helper, "glyph_chan_ext", 2.5, spell(MethodSelf.INSTANCE, glyph("cone_of_cold", 1),
            com.hollingsworth.arsnouveau.common.spell.augment.AugmentExtendTime.INSTANCE));
        ServerPlayer shorter = channeller(helper, "glyph_chan_short", 4.5, spell(MethodSelf.INSTANCE, glyph("cone_of_cold", 1),
            com.hollingsworth.arsnouveau.common.spell.augment.AugmentDurationDown.INSTANCE));
        helper.succeedWhen(() -> {
            int b = pulses(helper, base);
            int e = pulses(helper, longer);
            int s = pulses(helper, shorter);
            helper.assertTrue(e == b + 2, "Extend Time adds two paid pulses: base " + b + ", extended " + e);
            helper.assertTrue(s == Math.max(0, b - 2), "Duration Down removes two: base " + b + ", shortened " + s);
        });
    }

    // ---- Nested composition ----

    /** An Ars spell exported to an Iron's scroll can carry an imported glyph: one heal, each leg paid once. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_exportedScrollCarryingAGlyph_runsOncePaysEachLegOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Spell inner = spell(MethodSelf.INSTANCE, glyph("heal", 1));
        ItemStack scroll = com.otectus.arsnspells.spell.ArsSpellExportUtil.createIronsScrollCarrier(inner);
        helper.assertTrue(!scroll.isEmpty(), "the export must produce a real Iron's scroll carrier");
        ServerPlayer player = caster(helper, "glyph_nested_scroll", GameType.SURVIVAL);
        fund(player);
        player.setHealth(10.0f);
        double outer = ImportedGlyphPaymentGameTests.quoteTotal(com.otectus.arsnspells.casting.QuoteService.quote(player,
            ResourceUnit.ARS_MANA, inner.getCost(), com.otectus.arsnspells.casting.QuoteService.currentRules(),
            com.otectus.arsnspells.contract.CarrierPolicy.REUSABLE_BOOK_SEMANTICS));
        double glyphLeg = ImportedGlyphPaymentGameTests.glyphQuote(player, ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "heal"), 1);
        double start = pools(player);
        int[] heals = {0};
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingHealEvent> counter = event -> {
            if (event.getEntity() == player) heals[0]++;
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingHealEvent.class, counter);
        try {
            TouchCastGameTests.request(player, scroll, TouchCastGameTests.hintAt(player));
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(counter);
        }
        helper.assertTrue(player.getHealth() > 10.0f && heals[0] == 1, "the nested glyph heals exactly once, got " + heals[0]);
        helper.assertTrue(IronsProxyCastDriver.onNativeCooldown(player, "irons_spellbooks:heal"), "the nested glyph starts Heal's own cooldown");
        helper.assertTrue(Math.abs((start - pools(player)) - (outer + glyphLeg)) < 0.02,
            "the carrier's Ars leg and the glyph's leg are each premiumed and paid once: spent " + (start - pools(player))
                + ", expected " + outer + " + " + glyphLeg);
        helper.succeed();
    }

    /** The same spell bound into an Iron's book and cast from the wheel runs inside the proxy without disturbing it. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_boundBookWheelCarryingAGlyph_keepsTheParentCastIntact(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack book = IronsTableDriver.freshBook();
        var bound = com.otectus.arsnspells.spell.IronsBookBindingUtil.appendArsSpellToBook(book,
            com.otectus.arsnspells.spell.CrossCastingHandler.encodeArsSpell(spell(MethodSelf.INSTANCE, glyph("heal", 1))),
            "Glyph Heal", "water", "heart", -1);
        helper.assertTrue(bound.wasAdded(), "binding must add, got " + bound);
        ServerPlayer player = caster(helper, "glyph_nested_wheel", GameType.SURVIVAL);
        fund(player);
        player.setHealth(10.0f);
        int index = IronsProxyCastDriver.proxySlotIndex(book, 1);
        helper.assertTrue(index >= 0, "the bound proxy must occupy a wheel slot");
        helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(player, book, index), "Iron's must start the wheel cast");
        IronsProxyCastDriver.tickUntilCastEnds(player);
        helper.assertTrue(player.getHealth() > 10.0f, "the glyph inside the bound spell must heal");
        helper.assertTrue(IronsProxyCastDriver.onNativeCooldown(player, "irons_spellbooks:heal"), "the nested glyph starts Heal's own cooldown");
        helper.assertTrue(!IronsProxyCastDriver.castingSpell(player, "irons_spellbooks:heal"),
            "the nested glyph never takes over the player's own Iron's casting slot");
        helper.succeed();
    }

    /** An imported glyph cast during an unrelated native Iron's long cast leaves that cast running. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_glyphDoesNotCancelAnUnrelatedNativeCast(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_beside_native", GameType.SURVIVAL);
        fund(player);
        player.setHealth(10.0f);
        ItemStack fireballBook = IronsProxyCastDriver.nativeBook("irons_spellbooks:fireball");
        helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(player, fireballBook, 0), "the native fireball must start charging");
        helper.assertTrue(IronsProxyCastDriver.castingSpell(player, "irons_spellbooks:fireball"), "the native fireball is charging");
        cast(player, spell(MethodSelf.INSTANCE, glyph("heal", 1)));
        helper.assertTrue(player.getHealth() > 10.0f, "the glyph runs");
        helper.assertTrue(IronsProxyCastDriver.castingSpell(player, "irons_spellbooks:fireball"),
            "the native fireball cast must still be charging after the glyph ran");
        IronsProxyCastDriver.resetCastingState(player);
        helper.succeed();
    }

    private static void wall(GameTestHelper helper, net.minecraft.world.level.block.Block block) {
        for (int x = 3; x <= 4; x++) for (int y = 2; y <= 4; y++) for (int z = 0; z <= 4; z++) helper.setBlock(new BlockPos(x, y, z), block);
    }

    private static float healed(GameTestHelper helper, String scenario, Spell spell) {
        ServerPlayer player = caster(helper, scenario, GameType.SURVIVAL);
        fund(player);
        player.setHealth(1.0f);
        cast(player, spell);
        return player.getHealth() - 1.0f;
    }

    private static double launchSpeed(GameTestHelper helper, String scenario, Spell spell) {
        ServerPlayer player = caster(helper, scenario, GameType.SURVIVAL);
        fund(player);
        aim(player, helper.absoluteVec(new Vec3(4.5, 3.6, 2.5)));
        cast(player, spell);
        var launched = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
            player.getBoundingBox().inflate(4.0), p -> p.getOwner() == player);
        helper.assertTrue(launched.size() == 1, scenario + ": one native projectile must be launched, got " + launched.size());
        double speed = launched.get(0).getDeltaMovement().length();
        launched.forEach(net.minecraft.world.entity.Entity::discard);
        return speed;
    }

    private static ServerPlayer channeller(GameTestHelper helper, String scenario, double z, Spell spell) {
        ServerPlayer player = caster(helper, scenario, GameType.SURVIVAL);
        fund(player);
        player.moveTo(helper.absoluteVec(new Vec3(0.5, 2.0, z)));
        aim(player, helper.absoluteVec(new Vec3(4.5, 2.5, z)));
        GlyphSessions.clearOutcomes(player.getUUID());
        cast(player, spell);
        return player;
    }

    /** Pulses a completed channel reported paying after its first, or fails while it is still running. */
    private static int pulses(GameTestHelper helper, ServerPlayer player) {
        var done = GlyphSessions.outcomes(player.getUUID()).stream().filter(o -> o.outcome() == GlyphOutcome.COMPLETED).findFirst();
        helper.assertTrue(done.isPresent(), player.getGameProfile().getName() + " has not finished channelling");
        var matcher = java.util.regex.Pattern.compile("(\\d+) paid pulse").matcher(done.get().detail());
        helper.assertTrue(matcher.find(), "unexpected channel detail " + done.get().detail());
        return Integer.parseInt(matcher.group(1));
    }

    // ---- Fixtures ----

    static ImportedIronEffect glyph(String spell, int level) {
        return ImportedGlyphRegistration.effect(new ImportedGlyphKey(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", spell), level));
    }

    static Spell spell(AbstractSpellPart... parts) {
        return new Spell(parts);
    }

    /** Cast {@code spell} from a creative Ars spellbook in the main hand, exactly as Ars's own right-click does. */
    static void cast(ServerPlayer player, Spell spell) {
        ItemStack book = new ItemStack(ItemsRegistry.CREATIVE_SPELLBOOK.get());
        SpellCasterRegistry.from(book).setSpell(spell).saveToStack(book);
        player.setItemInHand(InteractionHand.MAIN_HAND, book);
        SpellCasterRegistry.from(book).castSpell(player.level(), player, InteractionHand.MAIN_HAND, null);
    }

    static ServerPlayer caster(GameTestHelper helper, String scenario, GameType mode) {
        String name = scenario.length() > 16 ? scenario.substring(0, 16) : scenario;
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
            UUID.nameUUIDFromBytes(("ans_gametest/" + scenario).getBytes(StandardCharsets.UTF_8)), name));
        player.moveTo(helper.absoluteVec(STAND));
        player.setGameMode(mode);
        player.setHealth(player.getMaxHealth());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.removeAllEffects();
        IronsProxyCastDriver.resetCastingState(player);
        clearCooldowns(player);
        GlyphSessions.closeAllFor(player.getUUID(), "test reset");
        return player;
    }

    static void clearCooldowns(ServerPlayer player) {
        if (IronsCompat.isLoaded()) IronsProxyCastDriver.clearCooldowns(player);
    }

    static Pig pig(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, TARGET);
        pig.setNoAi(true);
        return pig;
    }

    static void aim(ServerPlayer player, Vec3 at) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, at);
        player.setYHeadRot(player.getYRot());
    }

    static void fund(ServerPlayer player) {
        NativeManaAccess.with(player, ResourceUnit.ARS_MANA, () -> {
            var mana = CapabilityRegistry.getMana(player);
            mana.setMaxMana(10000);
            mana.setMana(10000);
            return null;
        });
        IronsProxyCastDriver.setMaxIronsMana(player, 10000.0);
        IronsProxyCastDriver.setIronsMana(player, 10000.0f);
    }

    static double pools(ServerPlayer player) {
        double ars = NativeManaAccess.with(player, ResourceUnit.ARS_MANA, () -> CapabilityRegistry.getMana(player).getCurrentMana());
        return ars + IronsProxyCastDriver.ironsMana(player);
    }

    static boolean hasEffect(net.minecraft.world.entity.LivingEntity entity, String id) {
        var holder = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(id));
        return holder.isPresent() && entity.hasEffect(holder.get());
    }
}
