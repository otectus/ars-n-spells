package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.util.CasterUtil;
import com.hollingsworth.arsnouveau.common.entity.EntityProjectileSpell;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentSensitive;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectBreak;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectFreeze;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHarm;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.otectus.arsnspells.bridge.NativeManaAccess;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.network.CarrierFingerprint;
import com.otectus.arsnspells.network.CrossCastRequestPacket;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import com.otectus.arsnspells.spell.target.TargetHint;
import com.otectus.arsnspells.util.ManaUtil;
import com.otectus.arsnspells.util.SpellAnalysis;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * 3.4.0 Phase A: Ars spells containing Touch, cast from every ANS carrier route on Forge 1.20.1.
 *
 * <p>Every scenario starts the cast through the entry the server really uses: Ars's own
 * {@code SpellBook.use} through {@code ServerPlayerGameMode.useItem} for the native control,
 * {@link CrossCastRequestPacket#process} (admission, fingerprint, then the cast handler) for a
 * generic carrier or an exported Iron's scroll, and Iron's own hotkey, staff and Curios entry
 * points plus its cast ticker for a bound Iron's book. The observable is always the world:
 * a broken block, a hurt mob, a frozen water source, a launched projectile, a healed caster.
 *
 * <p>The reported failure (an exported scroll's Touch cast doing nothing) is a NeoForge 1.21.1
 * defect: that port calls {@code SpellResolver.onCast} directly, and Ars's {@code MethodTouch}
 * fails there by design. This Forge line has always gone through Ars's own caster, which picks
 * the target and dispatches to the block and entity entries, and these scenarios establish that.
 * The defect this build did have on the Touch path is {@link #touchMiss_genericCarrier_spendsAndTrainsNothing}:
 * a miss trained the school, because ANS credited affinity on {@code SpellCastEvent}, which Ars
 * posts before the form runs.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class TouchRegressionGameTests {

    // ---- Native control -------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void touchBreak_nativeArsBook_breaksTheTargetedBlock(GameTestHelper helper) {
        ServerPlayer player = aimingPlayer(helper, "touch_native_blk");
        BlockPos target = dirtInFront(helper);
        aimAt(player, helper.absoluteVec(Vec3.atCenterOf(target)));
        ItemStack book = nativeBook(touchBreak());
        player.setItemInHand(InteractionHand.MAIN_HAND, book);
        player.gameMode.useItem(player, helper.getLevel(), book, InteractionHand.MAIN_HAND);
        helper.assertBlockPresent(Blocks.AIR, target);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void touchMiss_nativeArsBook_trainsNothing(GameTestHelper helper) {
        ServerPlayer player = aimingPlayer(helper, "touch_native_miss");
        player.setGameMode(GameType.SURVIVAL);
        fundArs(player);
        lookAtSky(player);
        ItemStack book = nativeBook(touchBreak());
        player.setItemInHand(InteractionHand.MAIN_HAND, book);
        int affinity = affinity(player, touchBreak());
        double mana = arsMana(player);
        player.gameMode.useItem(player, helper.getLevel(), book, InteractionHand.MAIN_HAND);
        helper.assertTrue(arsMana(player) == mana, "a native Touch miss must not cost mana");
        helper.assertTrue(affinity(player, touchBreak()) == affinity,
            "a native Touch miss must not train the spell's school: Ars posts SpellCastEvent before Touch fails, "
                + "so training is credited only when the resolver spends");
        helper.succeed();
    }

    // ---- Generic carriers through the real request path -----------------------------------

    @GameTest(template = "platform")
    public static void touchBreak_genericCarrier_breaksTheTargetedBlock(GameTestHelper helper) {
        ServerPlayer player = aimingPlayer(helper, "touch_generic_blk");
        BlockPos target = dirtInFront(helper);
        aimAt(player, helper.absoluteVec(Vec3.atCenterOf(target)));
        ItemStack carrier = genericCarrier(touchBreak());
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        helper.assertTrue(request(player, carrier, TargetHint.block(helper.absolutePos(target), Direction.UP)),
            "the request for an aimed Touch cast must resolve");
        helper.assertBlockPresent(Blocks.AIR, target);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void touchHarm_genericCarrier_harmsTheTargetedMob(GameTestHelper helper) {
        ServerPlayer player = aimingPlayer(helper, "touch_generic_mob");
        Pig pig = pigInFront(helper);
        aimAt(player, pig.getBoundingBox().getCenter());
        ItemStack carrier = genericCarrier(new Spell(MethodTouch.INSTANCE, EffectHarm.INSTANCE));
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        float before = pig.getHealth();
        helper.assertTrue(request(player, carrier, TargetHint.entity(pig.getId())), "the aimed Touch cast must resolve");
        helper.assertTrue(pig.getHealth() < before, "Touch -> Harm must hurt the mob it was aimed at");
        helper.assertTrue(player.getHealth() == 10.0f, "Touch -> Harm must not hurt the caster");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void touchSensitiveFreeze_genericCarrier_freezesTheWaterItWasAimedAt(GameTestHelper helper) {
        ServerPlayer player = aimingPlayer(helper, "touch_sensitive");
        BlockPos water = new BlockPos(3, 2, 1);
        helper.setBlock(water, Blocks.WATER);
        aimAt(player, helper.absoluteVec(Vec3.atCenterOf(water)));
        ItemStack carrier = genericCarrier(new Spell(MethodTouch.INSTANCE, AugmentSensitive.INSTANCE, EffectFreeze.INSTANCE));
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        helper.assertTrue(request(player, carrier, TargetHint.NONE), "Sensitive Touch at water must resolve");
        helper.assertBlockPresent(Blocks.ICE, water);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void selfHeal_genericCarrier_healsTheCaster(GameTestHelper helper) {
        ServerPlayer player = aimingPlayer(helper, "touch_ctl_self");
        BlockPos target = dirtInFront(helper);
        aimAt(player, helper.absoluteVec(Vec3.atCenterOf(target)));
        ItemStack carrier = genericCarrier(new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE));
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        helper.assertTrue(request(player, carrier, TargetHint.block(helper.absolutePos(target), Direction.UP)),
            "a Self cast aimed at a block must still resolve");
        helper.assertTrue(player.getHealth() > 10.0f, "Self -> Heal must heal the caster even when a block is targeted");
        helper.assertBlockPresent(Blocks.DIRT, target);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void projectileHarm_genericCarrier_launchesItsProjectile(GameTestHelper helper) {
        ServerPlayer player = aimingPlayer(helper, "touch_ctl_proj");
        BlockPos target = dirtInFront(helper);
        aimAt(player, helper.absoluteVec(Vec3.atCenterOf(target)));
        ItemStack carrier = genericCarrier(new Spell(MethodProjectile.INSTANCE, EffectHarm.INSTANCE));
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        AABB around = new AABB(player.blockPosition()).inflate(4);
        int before = helper.getLevel().getEntitiesOfClass(EntityProjectileSpell.class, around).size();
        helper.assertTrue(request(player, carrier, TargetHint.block(helper.absolutePos(target), Direction.UP)),
            "a Projectile cast aimed at a block must still resolve");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(EntityProjectileSpell.class, around).size() > before,
            "Projectile -> Harm must launch its projectile instead of acting on the block it was aimed at");
        helper.assertBlockPresent(Blocks.DIRT, target);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void touchMiss_genericCarrier_spendsAndTrainsNothing(GameTestHelper helper) {
        ServerPlayer player = aimingPlayer(helper, "touch_generic_miss");
        player.setGameMode(GameType.SURVIVAL);
        fundArs(player);
        lookAtSky(player);
        ItemStack carrier = genericCarrier(touchBreak());
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        int affinity = affinity(player, touchBreak());
        double mana = arsMana(player);
        helper.assertTrue(!request(player, carrier, TargetHint.NONE), "a Touch cast with nothing in reach must not succeed");
        helper.assertTrue(arsMana(player) == mana, "a Touch miss must not cost mana");
        helper.assertTrue(affinity(player, touchBreak()) == affinity, "a Touch miss must not train the spell's school");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void touchHit_genericCarrier_paysAndTrainsOnce(GameTestHelper helper) {
        ServerPlayer player = aimingPlayer(helper, "touch_generic_paid");
        player.setGameMode(GameType.SURVIVAL);
        fundArs(player);
        BlockPos target = dirtInFront(helper);
        aimAt(player, helper.absoluteVec(Vec3.atCenterOf(target)));
        ItemStack carrier = genericCarrier(touchBreak());
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        int affinity = affinity(player, touchBreak());
        double mana = arsMana(player);
        helper.assertTrue(request(player, carrier, TargetHint.block(helper.absolutePos(target), Direction.UP)),
            "a funded survival Touch cast must resolve");
        helper.assertBlockPresent(Blocks.AIR, target);
        helper.assertTrue(arsMana(player) < mana, "a resolved survival Touch cast must be paid");
        helper.assertTrue(affinity(player, touchBreak()) == affinity + 1,
            "a resolved Touch cast must train its school exactly once, got " + (affinity(player, touchBreak()) - affinity));
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void touchOutOfReach_genericCarrier_isAMiss(GameTestHelper helper) {
        ServerPlayer player = aimingPlayer(helper, "touch_out_of_reach");
        BlockPos target = new BlockPos(4, 2, 4);
        helper.setBlock(target, Blocks.DIRT);
        aimAt(player, helper.absoluteVec(Vec3.atCenterOf(target)));
        var reach = player.getAttribute(ForgeMod.BLOCK_REACH.get());
        AttributeModifier shortArms = new AttributeModifier(UUID.nameUUIDFromBytes("ans/test/short_arms".getBytes()),
            "ANS test: short reach", -4.0, AttributeModifier.Operation.ADDITION);
        reach.addTransientModifier(shortArms);
        try {
            ItemStack carrier = genericCarrier(touchBreak());
            player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
            helper.assertTrue(!request(player, carrier, TargetHint.block(helper.absolutePos(target), Direction.UP)),
                "a block beyond the caster's reach is a miss even when the client claims it");
            helper.assertBlockPresent(Blocks.DIRT, target);
        } finally {
            reach.removeModifier(shortArms);
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void staleOrForgedHint_isNeverAuthority(GameTestHelper helper) {
        ServerPlayer player = aimingPlayer(helper, "touch_forged_hint");
        BlockPos aimed = dirtInFront(helper);
        BlockPos claimed = new BlockPos(0, 2, 4);
        helper.setBlock(claimed, Blocks.DIRT);
        aimAt(player, helper.absoluteVec(Vec3.atCenterOf(aimed)));
        ItemStack carrier = genericCarrier(touchBreak());
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        helper.assertTrue(request(player, carrier, TargetHint.block(helper.absolutePos(claimed), Direction.NORTH)),
            "a cast with a stale hint still resolves against the server's own pick");
        helper.assertBlockPresent(Blocks.AIR, aimed);
        helper.assertBlockPresent(Blocks.DIRT, claimed);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void duplicateRequestId_castsOnce(GameTestHelper helper) {
        ServerPlayer player = aimingPlayer(helper, "touch_duplicate");
        player.setHealth(4.0f);
        ItemStack carrier = genericCarrier(new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE));
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        UUID id = UUID.randomUUID();
        helper.assertTrue(CrossCastRequestPacket.process(player, packet(carrier, id, TargetHint.NONE)), "the first request must cast");
        float healed = player.getHealth();
        helper.assertTrue(healed > 4.0f, "the first request must heal the caster");
        helper.assertTrue(!CrossCastRequestPacket.process(player, packet(carrier, id, TargetHint.NONE)),
            "a replayed request id must be refused");
        helper.assertTrue(player.getHealth() == healed, "a replayed request id must not cast a second time");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void vanillaUsePacketAfterRequest_doesNotCastAgain(GameTestHelper helper) {
        ServerPlayer player = aimingPlayer(helper, "touch_use_overlap");
        player.setHealth(4.0f);
        ItemStack carrier = genericCarrier(new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE));
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        helper.assertTrue(request(player, carrier, TargetHint.NONE), "the request must cast");
        float healed = player.getHealth();
        // The client's use-item packet for the same click arrives after the request: the server
        // side of RightClickItem must suppress the native use instead of casting a second time.
        player.gameMode.useItem(player, helper.getLevel(), carrier, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getHealth() == healed, "the native use of the same click must not cast again");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void offhandOrDeadSender_isRefused(GameTestHelper helper) {
        ServerPlayer player = aimingPlayer(helper, "touch_bad_sender");
        BlockPos target = dirtInFront(helper);
        aimAt(player, helper.absoluteVec(Vec3.atCenterOf(target)));
        ItemStack carrier = genericCarrier(touchBreak());
        player.setItemInHand(InteractionHand.OFF_HAND, carrier);
        CrossCastRequestPacket offhand = new CrossCastRequestPacket(InteractionHand.OFF_HAND, CrossCastRequestPacket.Action.CAST,
            0, UUID.randomUUID(), CarrierFingerprint.of(carrier), TargetHint.NONE);
        helper.assertTrue(!CrossCastRequestPacket.process(player, offhand), "an off-hand request must never cast");
        helper.assertBlockPresent(Blocks.DIRT, target);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        player.setHealth(0.0f);
        helper.assertTrue(!request(player, carrier, TargetHint.NONE), "a dead sender must never cast");
        helper.assertBlockPresent(Blocks.DIRT, target);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void staleCarrierFingerprint_isRefused(GameTestHelper helper) {
        ServerPlayer player = aimingPlayer(helper, "touch_stale_print");
        BlockPos target = dirtInFront(helper);
        aimAt(player, helper.absoluteVec(Vec3.atCenterOf(target)));
        ItemStack carrier = genericCarrier(touchBreak());
        String staleFingerprint = CarrierFingerprint.of(carrier);
        CrossCastingHandler.addCrossModSpell(carrier, new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE));
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        CrossCastRequestPacket stale = new CrossCastRequestPacket(InteractionHand.MAIN_HAND, CrossCastRequestPacket.Action.CAST,
            0, UUID.randomUUID(), staleFingerprint, TargetHint.NONE);
        helper.assertTrue(!CrossCastRequestPacket.process(player, stale), "a request made against an older carrier must be refused");
        helper.assertBlockPresent(Blocks.DIRT, target);
        helper.succeed();
    }

    // ---- Iron's carriers --------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void ironsLoaded_touchBreak_exportedScroll_breaksTheTargetedBlock(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = aimingPlayer(helper, "touch_scroll_blk");
        BlockPos target = dirtInFront(helper);
        aimAt(player, helper.absoluteVec(Vec3.atCenterOf(target)));
        ItemStack scroll = com.otectus.arsnspells.spell.ArsSpellExportUtil.createIronsScrollCarrier(touchBreak());
        helper.assertTrue(!scroll.isEmpty(), "the Loom export must produce a real Iron's scroll carrier");
        player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
        helper.assertTrue(request(player, scroll, TargetHint.block(helper.absolutePos(target), Direction.UP)),
            "the exported scroll's Touch cast must resolve");
        helper.assertBlockPresent(Blocks.AIR, target);
        helper.assertTrue(player.getMainHandItem() == scroll && !scroll.isEmpty(),
            "an exported carrier scroll is a reusable ANS carrier and is not consumed by casting");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_touchHarm_exportedScroll_harmsTheTargetedMob(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = aimingPlayer(helper, "touch_scroll_mob");
        Pig pig = pigInFront(helper);
        aimAt(player, pig.getBoundingBox().getCenter());
        ItemStack scroll = com.otectus.arsnspells.spell.ArsSpellExportUtil.createIronsScrollCarrier(
            new Spell(MethodTouch.INSTANCE, EffectHarm.INSTANCE));
        player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
        float before = pig.getHealth();
        helper.assertTrue(request(player, scroll, TargetHint.entity(pig.getId())), "the exported scroll's Touch cast must resolve");
        helper.assertTrue(pig.getHealth() < before, "Touch -> Harm from an exported scroll must hurt the mob it was aimed at");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_touchBreak_boundBookHotkey_breaksTheTargetedBlock(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack book = bound(helper, touchBreak(), "Touch Break Wheel");
        ServerPlayer player = aimingPlayer(helper, "touch_wheel_blk");
        BlockPos target = dirtInFront(helper);
        aimAt(player, helper.absoluteVec(Vec3.atCenterOf(target)));
        helper.assertTrue(IronsProxyCastDriver.hotkeyCast(player, book, slotOf(helper, book)),
            "Iron's must start the hotkey cast of the bound Touch spell from the Curios book");
        helper.assertBlockPresent(Blocks.AIR, target);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_touchHarm_boundBookStaff_harmsTheTargetedMob(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack book = bound(helper, new Spell(MethodTouch.INSTANCE, EffectHarm.INSTANCE), "Touch Harm Staff");
        ServerPlayer player = aimingPlayer(helper, "touch_staff_mob");
        Pig pig = pigInFront(helper);
        aimAt(player, pig.getBoundingBox().getCenter());
        float before = pig.getHealth();
        helper.assertTrue(IronsProxyCastDriver.castViaStaffRightClick(player, book, slotOf(helper, book), IronsProxyCastDriver.staff()),
            "Iron's must start the staff cast of the bound Touch spell");
        helper.assertTrue(pig.getHealth() < before, "a staff cast of Touch -> Harm must hurt the mob it was aimed at");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_touchMiss_boundBookHotkey_startsNoCooldown(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack book = bound(helper, touchBreak(), "Touch Miss Wheel");
        ServerPlayer player = aimingPlayer(helper, "touch_wheel_miss");
        lookAtSky(player);
        int affinity = affinity(player, touchBreak());
        IronsProxyCastDriver.hotkeyCast(player, book, slotOf(helper, book));
        helper.assertTrue(!IronsProxyCastDriver.onNativeCooldown(player,
                com.otectus.arsnspells.spell.irons.ArsCrossProxyRegistry.spellId(1).toString()),
            "a wheel Touch cast that hit nothing must not start the proxy's native cooldown");
        helper.assertTrue(affinity(player, touchBreak()) == affinity, "a wheel Touch miss must not train the spell's school");
        helper.succeed();
    }




    // ---- Resolver entry instrumentation ----------------------------------------------------

    /**
     * Both {@code onCastOnBlock} overloads must reach ANS's return hook: Ars's player caster uses
     * the {@code UseOnContext} one. 3.3.7 named the method without a descriptor, so Mixin bound
     * only the first overload ({@code BlockHitResult}); every player block cast skipped the hook,
     * reported "not resolved" to the cross-cast handler and kept its payment plan open until the
     * end of the server tick.
     */
    @GameTest(template = "platform")
    public static void resolverReturnHook_coversBothBlockOverloads(GameTestHelper helper) {
        ServerPlayer player = aimingPlayer(helper, "touch_overloads");
        BlockPos target = dirtInFront(helper);
        BlockPos second = new BlockPos(3, 2, 3);
        helper.setBlock(second, Blocks.DIRT);
        aimAt(player, helper.absoluteVec(Vec3.atCenterOf(target)));
        Spell spell = touchBreak();
        for (boolean useOnContext : new boolean[] {true, false}) {
            BlockPos pos = useOnContext ? target : second;
            com.otectus.arsnspells.spell.CrossCastContext.beginWithAttempt(player,
                com.otectus.arsnspells.spell.CrossSpellType.ARS_NOUVEAU, helper.getLevel().getGameTime(), UUID.randomUUID());
            try {
                var context = new com.hollingsworth.arsnouveau.api.spell.SpellContext(helper.getLevel(), spell, player,
                    new com.hollingsworth.arsnouveau.api.spell.wrapped_caster.PlayerCaster(player), ItemStack.EMPTY);
                var resolver = new com.hollingsworth.arsnouveau.api.spell.SpellResolver(context);
                var hit = new net.minecraft.world.phys.BlockHitResult(helper.absoluteVec(Vec3.atCenterOf(pos)), Direction.UP,
                    helper.absolutePos(pos), false);
                boolean ok = useOnContext
                    ? resolver.onCastOnBlock(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND, hit))
                    : resolver.onCastOnBlock(hit);
                helper.assertTrue(ok, "Touch -> Break through onCastOnBlock(" + (useOnContext ? "UseOnContext" : "BlockHitResult") + ") must resolve");
                var entry = com.otectus.arsnspells.spell.CrossCastContext.peek(player);
                helper.assertTrue(entry != null && Boolean.TRUE.equals(entry.resolved),
                    "ANS's resolver return hook must record onCastOnBlock(" + (useOnContext ? "UseOnContext" : "BlockHitResult") + ")");
                helper.assertTrue(!com.otectus.arsnspells.casting.ArsCastPayments.handles(context),
                    "the payment plan must be closed when onCastOnBlock(" + (useOnContext ? "UseOnContext" : "BlockHitResult") + ") returns");
            } finally {
                com.otectus.arsnspells.spell.CrossCastContext.clear(player);
            }
            helper.assertBlockPresent(Blocks.AIR, pos);
        }
        helper.succeed();
    }

    /**
     * Evidence for the regression record: the world effect and the result ANS reported, separately.
     * Logs one line; asserts only that the two agree.
     */
    @GameTest(template = "platform")
    public static void exportedOrGenericBlockCast_reportsWhatItDid(GameTestHelper helper) {
        ServerPlayer player = aimingPlayer(helper, "touch_evidence");
        BlockPos target = dirtInFront(helper);
        aimAt(player, helper.absoluteVec(Vec3.atCenterOf(target)));
        ItemStack carrier = genericCarrier(touchBreak());
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        boolean reported = request(player, carrier, TargetHint.block(helper.absolutePos(target), Direction.UP));
        boolean broken = helper.getLevel().getBlockState(helper.absolutePos(target)).isAir();
        org.slf4j.LoggerFactory.getLogger("ANS-TouchEvidence").info("[ANS-TouchEvidence] generic Touch->Break at a block: effect_applied={} reported_resolved={}",
            broken, reported);
        helper.assertTrue(broken == reported, "ANS reported resolved=" + reported + " for a cast whose effect_applied=" + broken);
        helper.succeed();
    }

    // ---- Fixtures ----------------------------------------------------------------------------

    static Spell touchBreak() {
        return new Spell(MethodTouch.INSTANCE, EffectBreak.INSTANCE);
    }

    /**
     * A creative, hurt, empty-handed player standing on the platform corner. In GameTestHelper
     * coordinates the template's floor is relative y=1 (the structure block sits below it), so
     * the player's feet and every target block are at y=2.
     */
    static ServerPlayer aimingPlayer(GameTestHelper helper, String scenario) {
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, scenario);
        player.moveTo(helper.absoluteVec(new Vec3(0.5, 2.0, 0.5)));
        return player;
    }

    static BlockPos dirtInFront(GameTestHelper helper) {
        BlockPos target = new BlockPos(2, 2, 2);
        helper.setBlock(target, Blocks.DIRT);
        return target;
    }

    static Pig pigInFront(GameTestHelper helper) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(2.5, 2.0, 2.5));
        pig.setInvulnerable(false);
        return pig;
    }

    static void aimAt(ServerPlayer player, Vec3 point) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, point);
    }

    static void lookAtSky(ServerPlayer player) {
        player.setXRot(-90.0f);
        player.setYRot(0.0f);
        player.setYHeadRot(0.0f);
    }

    static ItemStack genericCarrier(Spell spell) {
        ItemStack carrier = new ItemStack(Items.STICK);
        CrossCastingHandler.addCrossModSpell(carrier, spell);
        return carrier;
    }

    static ItemStack nativeBook(Spell spell) {
        ItemStack book = new ItemStack(ItemsRegistry.ARCHMAGE_SPELLBOOK.get());
        var caster = CasterUtil.getCaster(book);
        caster.setSpell(spell, 0);
        caster.setCurrentSlot(0);
        return book;
    }

    static CrossCastRequestPacket packet(ItemStack carrier, UUID id, TargetHint hint) {
        return new CrossCastRequestPacket(InteractionHand.MAIN_HAND, CrossCastRequestPacket.Action.CAST, 0, id,
            CarrierFingerprint.of(carrier), hint);
    }

    /** Send one fresh cast request the way a received packet is processed. */
    static boolean request(ServerPlayer player, ItemStack carrier, TargetHint hint) {
        return CrossCastRequestPacket.process(player, packet(carrier, UUID.randomUUID(), hint));
    }

    static void fundArs(ServerPlayer player) {
        NativeManaAccess.with(player, ResourceUnit.ARS_MANA, () -> {
            var mana = ManaUtil.getNativeMana(player).orElseThrow(IllegalStateException::new);
            mana.setMaxMana(10000);
            mana.setMana(10000);
            return null;
        });
        if (IronsCompat.isLoaded()) IronsProxyCastDriver.setIronsMana(player, 10000.0f);
    }

    static double arsMana(ServerPlayer player) {
        double ars = NativeManaAccess.with(player, ResourceUnit.ARS_MANA,
            () -> ManaUtil.getNativeMana(player).orElseThrow(IllegalStateException::new).getCurrentMana());
        return IronsCompat.isLoaded() ? ars + IronsProxyCastDriver.ironsMana(player) : ars;
    }

    static int affinity(ServerPlayer player, Spell spell) {
        String key = SpellAnalysis.analyze(spell).schoolKey();
        return player.getCapability(AffinityData.AFFINITY_DATA).map(data -> data.getLevel(key)).orElse(0);
    }

    static ItemStack bound(GameTestHelper helper, Spell spell, String name) {
        Item item = CrossCastGameTests.findIronsSpellBook();
        helper.assertTrue(item != null, "no Iron's spellbook item is registered despite Iron's being loaded");
        ItemStack book = new ItemStack(item);
        IronsBookBindingUtil.AppendResult result = IronsBookBindingUtil.appendArsSpellToBook(
            book, spell.serialize(), name, "water", "heart", -1);
        helper.assertTrue(result == IronsBookBindingUtil.AppendResult.ADDED, "binding " + name + " must ADD, got " + result);
        helper.assertTrue(CrossCastNbt.findEntryByProxyPoolId(book.getOrCreateTag(), 1) != null, name + " must land in proxy pool 1");
        return book;
    }

    static int slotOf(GameTestHelper helper, ItemStack book) {
        int index = IronsProxyCastDriver.proxySlotIndex(book, 1);
        helper.assertTrue(index >= 0, "the bound proxy must occupy a wheel slot in the book's Iron's container");
        return index;
    }

}
