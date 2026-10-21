package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.registry.SpellCasterRegistry;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentSensitive;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectBreak;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHarm;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectLight;
import com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.bridge.NativeManaAccess;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.network.CarrierFingerprint;
import com.otectus.arsnspells.network.CrossCastRequestPayload;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import com.otectus.arsnspells.spell.target.CastTargetHint;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The reported Touch failure and the 3.4.0 repair, driven through the real input paths.
 *
 * <p>Report (Minecraft 1.21.1, NeoForge): an Ars spell that starts with Touch, carried on a
 * scroll, does nothing. Cause: every ANS carrier resolved its Ars spell through
 * {@code SpellResolver.onCast}, and Ars's Touch form always fails there; it resolves only through
 * {@code onCastOnBlock} and {@code onCastOnEntity}. Before the repair these scenarios failed with
 * the dirt still standing and the pig unhurt (see {@code docs/3.4.0/touch-regression.md}).
 *
 * <p>Scroll and generic-carrier scenarios send the cross-cast request payload a client sends on
 * right-click through {@code handleOnServer}, with admission, the carrier fingerprint and target
 * validation in the way. Wheel, staff and Curios scenarios go through Iron's own cast entry
 * points. Every assertion is a world effect, a resource delta or a cast count, never a
 * {@code true} return.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class TouchCastGameTests {

    /** The caster stands here (feet) on the platform floor, which is relative y=1 in this harness. */
    private static final Vec3 STAND = new Vec3(1.5, 2.0, 2.5);
    /** At eye height, two blocks in front of the caster. */
    private static final BlockPos FRONT = new BlockPos(3, 3, 2);
    /** Where a living target stands, two blocks in front of the caster. */
    private static final Vec3 TARGET = new Vec3(3.5, 2.0, 2.5);

    private TouchCastGameTests() {
    }

    // ---- Touch regression: carriers ----

    /** The reported carrier: a real Iron's scroll exported by the Spell Loom with an Ars sidecar. */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void ironsLoaded_exportedScroll_touchBreak_breaksTheBlockInFront(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack scroll = ArsSpellExportUtil.createIronsScrollCarrier(touchBreak());
        helper.assertTrue(!scroll.isEmpty(), "the Spell Loom export must produce a real Iron's scroll carrier");
        ServerPlayer player = caster(helper, "touch_scroll");
        helper.setBlock(FRONT, Blocks.DIRT);
        aimAtBlock(helper, player, FRONT);
        request(player, scroll, hintAt(player));
        helper.assertBlockPresent(Blocks.AIR, FRONT);
        helper.assertTrue(scroll.getCount() == 1 && CrossModSpellComponents.has(scroll),
            "an exported carrier is reusable: the cast must not consume or blank it");
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_touch")
    public static void ironsLoaded_exportedScroll_touchHarm_hurtsTheLivingTarget(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack scroll = ArsSpellExportUtil.createIronsScrollCarrier(new Spell(MethodTouch.INSTANCE, EffectHarm.INSTANCE));
        ServerPlayer player = caster(helper, "touch_scroll_harm");
        Pig pig = pig(helper, TARGET);
        float before = pig.getHealth();
        aim(player, pig.getBoundingBox().getCenter());
        request(player, scroll, hintAt(player));
        helper.assertTrue(pig.getHealth() < before, "Touch -> Harm from an exported scroll must hurt the pig in front; "
            + before + " -> " + pig.getHealth());
        helper.succeed();
    }

    /** The same spell on a generic ANS-inscribed item. */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void genericCarrier_touchBreak_breaksTheBlockInFront(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_generic");
        helper.setBlock(FRONT, Blocks.DIRT);
        aimAtBlock(helper, player, FRONT);
        request(player, carrier(touchBreak()), hintAt(player));
        helper.assertBlockPresent(Blocks.AIR, FRONT);
        helper.succeed();
    }

    /** An old-style request with no aim at all still resolves Touch from the player's rotation. */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void genericCarrier_touchBreak_withoutHint_usesTheServerPick(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_nohint");
        helper.setBlock(FRONT, Blocks.DIRT);
        aimAtBlock(helper, player, FRONT);
        request(player, carrier(touchBreak()), CastTargetHint.ABSENT);
        helper.assertBlockPresent(Blocks.AIR, FRONT);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_touch")
    public static void genericCarrier_touchHarm_hurtsTheLivingTarget(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_harm");
        Pig pig = pig(helper, TARGET);
        float before = pig.getHealth();
        aim(player, pig.getBoundingBox().getCenter());
        request(player, carrier(new Spell(MethodTouch.INSTANCE, EffectHarm.INSTANCE)), hintAt(player));
        helper.assertTrue(pig.getHealth() < before, "Touch -> Harm cast from a carrier must hurt the living "
            + "target in front of the caster; health " + before + " -> " + pig.getHealth());
        helper.succeed();
    }

    // ---- Native and form controls ----

    /** Control: Ars's own spellbook casting the same Touch spell breaks the same block. */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void control_nativeArsSpellbook_touchBreak_breaksTheBlock(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_native");
        helper.setBlock(FRONT, Blocks.DIRT);
        aimAtBlock(helper, player, FRONT);
        ItemStack book = new ItemStack(ItemsRegistry.CREATIVE_SPELLBOOK.get());
        SpellCasterRegistry.from(book).setSpell(touchBreak()).saveToStack(book);
        player.setItemInHand(InteractionHand.MAIN_HAND, book);
        SpellCasterRegistry.from(book).castSpell(player.level(), player, InteractionHand.MAIN_HAND, null);
        helper.assertBlockPresent(Blocks.AIR, FRONT);
        helper.succeed();
    }

    /** Self keeps its native meaning: it heals the caster even while a block is in front. */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void control_selfHeal_stillResolvesOnTheCaster(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_self");
        player.setHealth(10.0f);
        helper.setBlock(FRONT, Blocks.DIRT);
        aimAtBlock(helper, player, FRONT);
        request(player, carrier(new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE)), hintAt(player));
        helper.assertTrue(player.getHealth() > 10.0f, "Self -> Heal must still heal the caster");
        helper.assertBlockPresent(Blocks.DIRT, FRONT);
        helper.succeed();
    }

    /** Projectile keeps its native meaning: a projectile launches from the caster even when aimed at a block. */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void control_projectileHarm_stillLaunchesAProjectile(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_proj");
        helper.setBlock(FRONT, Blocks.DIRT);
        aimAtBlock(helper, player, FRONT);
        AABB around = new AABB(player.position(), player.position()).inflate(6.0);
        int before = helper.getLevel().getEntitiesOfClass(Projectile.class, around).size();
        request(player, carrier(new Spell(MethodProjectile.INSTANCE, EffectHarm.INSTANCE)), hintAt(player));
        int after = helper.getLevel().getEntitiesOfClass(Projectile.class, around).size();
        helper.assertTrue(after > before, "Projectile -> Harm must launch an Ars projectile (" + before + " -> " + after + ")");
        helper.assertBlockPresent(Blocks.DIRT, FRONT);
        helper.succeed();
    }

    // ---- Misses, reach, occlusion, Sensitive ----

    /** A genuine miss is refused before the resolver: nothing is cast and nothing is paid. */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void touchMiss_isRefusedWithoutCastOrPayment(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_miss");
        survival(player);
        openSky(helper);
        aim(player, player.getEyePosition().add(0.0, 30.0, 0.0));
        double before = pools(player);
        int casts = SpellCastCounter.count(player.getUUID(),
            () -> request(player, carrier(touchBreak()), hintAt(player)));
        helper.assertTrue(casts == 0, "a Touch with nothing in reach must not start a cast; casts=" + casts);
        helper.assertTrue(Math.abs(pools(player) - before) < 1.0e-3, "a refused Touch must not charge; "
            + before + " -> " + pools(player));
        helper.succeed();
    }

    /** A block beyond Ars's spellbook reach is not touched. */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void touchOutOfReach_doesNotBreakTheBlock(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_far");
        openSky(helper);
        BlockPos high = new BlockPos(1, 10, 2);
        helper.setBlock(high, Blocks.DIRT);
        aimAtBlock(helper, player, high);
        request(player, carrier(touchBreak()), hintAt(player));
        helper.assertBlockPresent(Blocks.DIRT, high);
        helper.setBlock(high, Blocks.AIR);
        helper.succeed();
    }

    /** A living target behind a wall is not touched; the wall is the target. */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void touchObstructedEntity_isNotHurt(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_wall");
        player.moveTo(helper.absoluteVec(new Vec3(0.5, 2.0, 2.5)));
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE);
        Pig pig = pig(helper, TARGET);
        float before = pig.getHealth();
        aim(player, pig.getBoundingBox().getCenter());
        request(player, carrier(new Spell(MethodTouch.INSTANCE, EffectHarm.INSTANCE)),
            CastTargetHint.entity(player.getYRot(), player.getXRot(), dim(player), pig.getId(), pig.getBoundingBox().getCenter()));
        helper.assertTrue(pig.getHealth() == before, "a pig behind a wall must not be touched, even when the client claims it");
        helper.succeed();
    }

    /** Sensitive keeps Ars's native meaning: Touch may resolve in mid-air (Light places a light there). */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void touchSensitive_resolvesInAir_whereAPlainTouchIsRefused(GameTestHelper helper) {
        ServerPlayer plain = caster(helper, "touch_air_plain");
        openSky(helper);
        aim(plain, plain.getEyePosition().add(0.0, 30.0, 0.0));
        request(plain, carrier(new Spell(MethodTouch.INSTANCE, EffectLight.INSTANCE)), hintAt(plain));
        AABB above = new AABB(plain.getEyePosition(), plain.getEyePosition()).inflate(1.5).expandTowards(0, 6, 0);
        helper.assertTrue(countLights(helper, above) == 0, "a plain Touch into air must be refused, placing nothing");

        ServerPlayer sensitive = caster(helper, "touch_air_sens");
        aim(sensitive, sensitive.getEyePosition().add(0.0, 30.0, 0.0));
        request(sensitive, carrier(new Spell(MethodTouch.INSTANCE, AugmentSensitive.INSTANCE, EffectLight.INSTANCE)), hintAt(sensitive));
        helper.assertTrue(countLights(helper, above) > 0, "Touch + Sensitive must resolve at the end of its reach in air, "
            + "as an Ars spellbook does, and Light must place a light there");
        clearLights(helper, above);
        helper.succeed();
    }

    // ---- Wheel, staff and Curios routing (Iron's) ----

    @GameTest(template = "platform", batch = "ans_touch")
    public static void ironsLoaded_wheelHotkey_touchBreak_breaksTheBlock(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack book = boundBook(helper, touchBreak(), "Touch Wheel");
        ServerPlayer player = caster(helper, "touch_wheel");
        IronsProxyCastDriver.resetCastingState(player);
        helper.setBlock(FRONT, Blocks.DIRT);
        aimAtBlock(helper, player, FRONT);
        helper.assertTrue(IronsProxyCastDriver.hotkeyCast(player, book, IronsProxyCastDriver.proxySlotIndex(book, 1)),
            "Iron's must start the wheel cast of the bound Touch spell");
        helper.assertBlockPresent(Blocks.AIR, FRONT);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_touch")
    public static void ironsLoaded_staffRightClick_touchHarm_hurtsTheTarget(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack book = boundBook(helper, new Spell(MethodTouch.INSTANCE, EffectHarm.INSTANCE), "Touch Staff");
        ServerPlayer player = caster(helper, "touch_staff");
        IronsProxyCastDriver.resetCastingState(player);
        Pig pig = pig(helper, TARGET);
        float before = pig.getHealth();
        aim(player, pig.getBoundingBox().getCenter());
        helper.assertTrue(IronsProxyCastDriver.castViaStaffRightClick(player, book,
                IronsProxyCastDriver.proxySlotIndex(book, 1), IronsProxyCastDriver.staff()),
            "Iron's must start the staff cast of the bound Touch spell");
        helper.assertTrue(pig.getHealth() < before, "a staff cast of a bound Touch -> Harm must hurt the pig; "
            + before + " -> " + pig.getHealth());
        helper.succeed();
    }

    /** The book in the Curios spellbook slot, cast with the hotkey and nothing in hand. */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void ironsLoaded_curiosBook_touchBreak_breaksTheBlock(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack book = boundBook(helper, touchBreak(), "Touch Curios");
        ServerPlayer player = caster(helper, "touch_curios");
        IronsProxyCastDriver.resetCastingState(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.setBlock(FRONT, Blocks.DIRT);
        aimAtBlock(helper, player, FRONT);
        IronsProxyCastDriver.equipSpellbook(player, book);
        helper.assertTrue(IronsProxyCastDriver.hotkeyCast(player, book, IronsProxyCastDriver.proxySlotIndex(book, 1)),
            "Iron's must start the hotkey cast from the Curios book");
        helper.assertBlockPresent(Blocks.AIR, FRONT);
        helper.succeed();
    }

    /** A Touch with nothing in reach from the wheel is refused, and the proxy's cooldown does not start. */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void ironsLoaded_wheelTouchMiss_startsNoProxyCooldown(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack book = boundBook(helper, touchBreak(), "Touch Wheel Miss");
        ServerPlayer player = caster(helper, "touch_wheel_miss");
        player.setGameMode(GameType.SURVIVAL);
        IronsProxyCastDriver.resetCastingState(player);
        openSky(helper);
        aim(player, player.getEyePosition().add(0.0, 30.0, 0.0));
        IronsProxyCastDriver.hotkeyCast(player, book, IronsProxyCastDriver.proxySlotIndex(book, 1));
        helper.assertTrue(!IronsProxyCastDriver.onNativeCooldown(player, "ars_n_spells:ars_cross_1"),
            "a Touch miss is not a successful cast and must not start the wheel slot's cooldown");
        helper.succeed();
    }

    // ---- Input authority ----

    /**
     * One click on a living target fires the entity-interact pair, the server's item use and the
     * request; only the request casts, once.
     */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void eventOverlap_entityClick_castsExactlyOnce(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_overlap_e");
        Pig pig = pig(helper, TARGET);
        aim(player, pig.getBoundingBox().getCenter());
        ItemStack carrier = carrier(new Spell(MethodTouch.INSTANCE, EffectHarm.INSTANCE));
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        float before = pig.getHealth();
        int casts = SpellCastCounter.count(player.getUUID(), () -> {
            NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteractSpecific(player, InteractionHand.MAIN_HAND,
                pig, pig.getBoundingBox().getCenter().subtract(pig.position())));
            NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, pig));
            NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND));
            request(player, carrier, hintAt(player));
        });
        helper.assertTrue(casts == 1, "one entity click must be exactly one cast; got " + casts);
        helper.assertTrue(pig.getHealth() < before, "and that cast must hurt the pig");
        helper.succeed();
    }

    /** One click on a block fires the block use and the item use on the server; only the request casts. */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void eventOverlap_blockClick_castsExactlyOnce_andSuppressesTheCarrierUseOn(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_overlap_b");
        helper.setBlock(FRONT, Blocks.DIRT);
        aimAtBlock(helper, player, FRONT);
        ItemStack carrier = carrier(touchBreak());
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(FRONT)), Direction.WEST,
            helper.absolutePos(FRONT), false);
        PlayerInteractEvent.RightClickBlock blockEvent = new PlayerInteractEvent.RightClickBlock(player,
            InteractionHand.MAIN_HAND, helper.absolutePos(FRONT), hit);
        int casts = SpellCastCounter.count(player.getUUID(), () -> {
            NeoForge.EVENT_BUS.post(blockEvent);
            NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND));
            request(player, carrier, hintAt(player));
        });
        helper.assertTrue(casts == 1, "one block click must be exactly one cast; got " + casts);
        helper.assertTrue(blockEvent.getUseItem().isFalse(), "a carrier's own use-on must give way to the cast");
        helper.assertTrue(!blockEvent.getUseBlock().isFalse(), "the block's own interaction must keep precedence");
        helper.assertBlockPresent(Blocks.AIR, FRONT);
        helper.succeed();
    }

    /** A replayed request id is admitted once. */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void duplicateRequestId_castsOnce(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_dup");
        Pig pig = pig(helper, TARGET);
        aim(player, pig.getBoundingBox().getCenter());
        ItemStack carrier = carrier(new Spell(MethodTouch.INSTANCE, EffectHarm.INSTANCE));
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        UUID id = UUID.randomUUID();
        int casts = SpellCastCounter.count(player.getUUID(), () -> {
            send(player, carrier, id, hintAt(player));
            send(player, carrier, id, hintAt(player));
        });
        helper.assertTrue(casts == 1, "a replayed request id must be ignored; casts=" + casts);
        helper.succeed();
    }

    /** Fresh ids sent in one tick are bounded by the admission burst. */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void freshIdSpam_isBoundedByTheBurst(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_spam");
        aim(player, player.getEyePosition().add(0.0, -1.0, 2.0));
        ItemStack carrier = carrier(new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE));
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        int burst = com.otectus.arsnspells.config.AnsConfig.NETWORK_REQUEST_BURST.get();
        int casts = SpellCastCounter.count(player.getUUID(), () -> {
            for (int i = 0; i < burst + 10; i++) send(player, carrier, UUID.randomUUID(), hintAt(player));
        });
        helper.assertTrue(casts <= burst, "fresh-id spam must be bounded by the burst of " + burst + "; casts=" + casts);
        helper.assertTrue(casts >= 1, "the first requests of a burst must still cast");
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_touch")
    public static void staleFingerprint_isRefused(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_stale");
        helper.setBlock(FRONT, Blocks.DIRT);
        aimAtBlock(helper, player, FRONT);
        ItemStack carrier = carrier(touchBreak());
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        CrossCastRequestPayload payload = new CrossCastRequestPayload(InteractionHand.MAIN_HAND,
            CrossCastRequestPayload.Action.CAST, 0, UUID.randomUUID(), "0".repeat(64), hintAt(player));
        CrossCastRequestPayload.handleOnServer(payload, GameTestPayloads.from(player));
        helper.assertBlockPresent(Blocks.DIRT, FRONT);
        helper.succeed();
    }

    /** A claimed entity far out of reach is not hit; the server's own pick (the block in front) stands. */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void forgedEntityClaim_cannotHitAnUnreachableEntity(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_forge_e");
        Pig far = pig(helper, new Vec3(3.5, 2.0, 0.5));
        far.moveTo(far.getX(), far.getY() + 12.0, far.getZ());
        float before = far.getHealth();
        helper.setBlock(FRONT, Blocks.DIRT);
        aimAtBlock(helper, player, FRONT);
        request(player, carrier(new Spell(MethodTouch.INSTANCE, EffectHarm.INSTANCE)),
            CastTargetHint.entity(player.getYRot(), player.getXRot(), dim(player), far.getId(), far.position()));
        helper.assertTrue(far.getHealth() == before, "a forged entity claim must never become a hit");
        helper.succeed();
    }

    /** A claimed block the player is not looking at is not broken. */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void forgedBlockClaim_cannotBreakAnUnseenBlock(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_forge_b");
        BlockPos claimed = new BlockPos(1, 2, 4);
        helper.setBlock(claimed, Blocks.DIRT);
        helper.setBlock(FRONT, Blocks.DIRT);
        aimAtBlock(helper, player, FRONT);
        BlockPos abs = helper.absolutePos(claimed);
        request(player, carrier(touchBreak()),
            CastTargetHint.block(player.getYRot(), player.getXRot(), dim(player), abs, Direction.UP, Vec3.atCenterOf(abs)));
        helper.assertBlockPresent(Blocks.DIRT, claimed);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_touch")
    public static void wrongDimensionClaim_isRefused(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_dim");
        helper.setBlock(FRONT, Blocks.DIRT);
        aimAtBlock(helper, player, FRONT);
        request(player, carrier(touchBreak()), CastTargetHint.aimOnly(player.getYRot(), player.getXRot(),
            ResourceLocation.withDefaultNamespace("the_nether")));
        helper.assertBlockPresent(Blocks.DIRT, FRONT);
        helper.succeed();
    }

    /** A claimed block in an unloaded chunk is never touched or loaded. */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void unloadedTargetClaim_isNotTouched(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_unloaded");
        helper.setBlock(FRONT, Blocks.DIRT);
        aimAtBlock(helper, player, FRONT);
        BlockPos unloaded = player.blockPosition().offset(100_000, 0, 100_000);
        boolean loadedBefore = player.level().isLoaded(unloaded);
        request(player, carrier(new Spell(MethodTouch.INSTANCE, EffectHarm.INSTANCE)),
            CastTargetHint.block(player.getYRot(), player.getXRot(), dim(player), unloaded, Direction.UP, Vec3.atCenterOf(unloaded)));
        helper.assertTrue(player.level().isLoaded(unloaded) == loadedBefore, "a claim must never load a chunk");
        helper.assertBlockPresent(Blocks.DIRT, FRONT);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_touch")
    public static void handMismatch_isRefused(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_hand");
        helper.setBlock(FRONT, Blocks.DIRT);
        aimAtBlock(helper, player, FRONT);
        ItemStack carrier = carrier(touchBreak());
        player.setItemInHand(InteractionHand.OFF_HAND, carrier);
        CrossCastRequestPayload payload = new CrossCastRequestPayload(InteractionHand.OFF_HAND,
            CrossCastRequestPayload.Action.CAST, 0, UUID.randomUUID(),
            CarrierFingerprint.of(carrier, player.level().registryAccess()), hintAt(player));
        CrossCastRequestPayload.handleOnServer(payload, GameTestPayloads.from(player));
        helper.assertBlockPresent(Blocks.DIRT, FRONT);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_touch")
    public static void deadSender_isRefused(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_dead");
        helper.setBlock(FRONT, Blocks.DIRT);
        aimAtBlock(helper, player, FRONT);
        ItemStack carrier = carrier(touchBreak());
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        player.setHealth(0.0f);
        send(player, carrier, UUID.randomUUID(), hintAt(player));
        player.setHealth(player.getMaxHealth());
        helper.assertBlockPresent(Blocks.DIRT, FRONT);
        helper.succeed();
    }

    /** A carrier in both hands: the main-hand request casts once; the offhand never sends a second. */
    @GameTest(template = "platform", batch = "ans_touch")
    public static void offhandCarrier_neverSendsASecondCast(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "touch_offhand");
        Pig pig = pig(helper, TARGET);
        aim(player, pig.getBoundingBox().getCenter());
        ItemStack main = carrier(new Spell(MethodTouch.INSTANCE, EffectHarm.INSTANCE));
        ItemStack off = carrier(new Spell(MethodTouch.INSTANCE, EffectHarm.INSTANCE));
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
        int casts = SpellCastCounter.count(player.getUUID(), () -> {
            request(player, main, hintAt(player));
            NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(player, InteractionHand.OFF_HAND));
            CrossCastRequestPayload offhand = new CrossCastRequestPayload(InteractionHand.OFF_HAND,
                CrossCastRequestPayload.Action.CAST, 0, UUID.randomUUID(),
                CarrierFingerprint.of(off, player.level().registryAccess()), hintAt(player));
            CrossCastRequestPayload.handleOnServer(offhand, GameTestPayloads.from(player));
        });
        helper.assertTrue(casts == 1, "a carrier in each hand must still be one cast per click; casts=" + casts);
        helper.succeed();
    }

    // ---- Fixtures ----

    static Spell touchBreak() {
        return new Spell(MethodTouch.INSTANCE, EffectBreak.INSTANCE);
    }

    static ItemStack carrier(Spell spell) {
        ItemStack carrier = new ItemStack(Items.PAPER);
        CrossCastingHandler.addCrossModSpell(carrier, spell);
        return carrier;
    }

    private static ItemStack boundBook(GameTestHelper helper, Spell spell, String name) {
        ItemStack book = IronsTableDriver.freshBook();
        helper.assertTrue(!book.isEmpty(), "no Iron's spell book is registered despite Iron's being loaded");
        IronsBookBindingUtil.AppendResult result = IronsBookBindingUtil.appendArsSpellToBook(
            book, CrossCastingHandler.encodeArsSpell(spell), name, "water", "heart", -1);
        helper.assertTrue(result.wasAdded(), "binding " + name + " must ADD, got " + result);
        return book;
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityType.PIG, at);
        pig.setNoAi(true);
        pig.setInvulnerable(false);
        return pig;
    }

    /** A creative caster standing on the platform, private to {@code scenario}, empty-handed. */
    static ServerPlayer caster(GameTestHelper helper, String scenario) {
        String name = scenario.length() > 16 ? scenario.substring(0, 16) : scenario;
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
            UUID.nameUUIDFromBytes(("ans_gametest/" + scenario).getBytes(StandardCharsets.UTF_8)), name));
        player.moveTo(helper.absoluteVec(STAND));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setGameMode(GameType.CREATIVE);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    /**
     * Clear the column above the caster, including the barrier ceiling the GameTest harness puts
     * over each structure, so a cast aimed straight up has nothing in Ars reach.
     */
    private static void openSky(GameTestHelper helper) {
        for (int y = 4; y <= 12; y++) helper.setBlock(new BlockPos(1, y, 2), Blocks.AIR);
    }

    private static void survival(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        NativeManaAccess.with(player, ResourceUnit.ARS_MANA, () -> {
            var mana = CapabilityRegistry.getMana(player);
            mana.setMaxMana(10000);
            mana.setMana(10000);
            return null;
        });
        if (IronsCompat.isLoaded()) IronsProxyCastDriver.setIronsMana(player, 10000.0f);
    }

    private static double pools(ServerPlayer player) {
        double ars = NativeManaAccess.with(player, ResourceUnit.ARS_MANA,
            () -> CapabilityRegistry.getMana(player).getCurrentMana());
        return ars + (IronsCompat.isLoaded() ? IronsProxyCastDriver.ironsMana(player) : 0.0);
    }

    static void aim(ServerPlayer player, Vec3 at) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, at);
        player.setYHeadRot(player.getYRot());
    }

    private static void aimAtBlock(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        aim(player, Vec3.atCenterOf(helper.absolutePos(relative)));
    }

    private static ResourceLocation dim(ServerPlayer player) {
        return player.level().dimension().location();
    }

    /** What the client's crosshair sends: its aim, and the block or entity the server would also pick. */
    static CastTargetHint hintAt(ServerPlayer player) {
        return CastTargetHint.aimOnly(player.getYRot(), player.getXRot(), dim(player));
    }

    /** What a client sends on a right-click with the carrier: a cast intent for the held stack. */
    static void request(ServerPlayer player, ItemStack carrier, CastTargetHint hint) {
        player.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        send(player, carrier, UUID.randomUUID(), hint);
    }

    private static void send(ServerPlayer player, ItemStack carrier, UUID id, CastTargetHint hint) {
        CrossCastRequestPayload payload = new CrossCastRequestPayload(InteractionHand.MAIN_HAND,
            CrossCastRequestPayload.Action.CAST, 0, id,
            CarrierFingerprint.of(carrier, player.level().registryAccess()), hint);
        CrossCastRequestPayload.handleOnServer(payload, GameTestPayloads.from(player));
    }

    private static int countLights(GameTestHelper helper, AABB box) {
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            var state = helper.getLevel().getBlockState(pos);
            if (state.is(BlockRegistry.LIGHT_BLOCK.get()) || state.is(BlockRegistry.T_LIGHT_BLOCK.get())) found++;
        }
        return found;
    }

    private static void clearLights(GameTestHelper helper, AABB box) {
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            var state = helper.getLevel().getBlockState(pos);
            if (state.is(BlockRegistry.LIGHT_BLOCK.get()) || state.is(BlockRegistry.T_LIGHT_BLOCK.get())) {
                helper.getLevel().setBlockAndUpdate(pos.immutable(), Blocks.AIR.defaultBlockState());
            }
        }
    }
}
