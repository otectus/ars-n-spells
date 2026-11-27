package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.casting.AlternativePayment;
import com.otectus.arsnspells.casting.PaymentTimeline;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.glyph.GlyphSessions;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 3.4.1 review PAYMENT-01, in the pinned Covenant of the Seven profile (Covenant 2.2.6-hotfix, Blood
 * Magic, Enigmatic Legacy, Nature's Aura): an imported glyph pays with the resource a native cast of
 * the same spell takes from the same caster, and admission never demands a resource that route does
 * not spend. Every raw LP movement is reconciled with the timeline's alternative legs; the imported
 * legs are told apart by their marker.
 *
 * <p>All tests share one batch: the ring toggles, the failure policy and the mana mode are globals.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class GlyphRouteGameTests {
    private static final String COVENANT = "covenant_of_the_seven";
    private static final String BATCH = "ans_review_route";
    private static final String HEAL = "irons_spellbooks:heal";
    private static final String FIREBALL = "irons_spellbooks:fireball";

    private GlyphRouteGameTests() {}

    /** Cursed Ring, LP but no Iron's mana: the imported Heal runs and pays LP, as a native Heal does, and no mana. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 200)
    public static void covenantLoaded_route_cursedRingImportedGlyphPaysLpLikeANativeCast(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, COVENANT)) return;
        ServerPlayer player = ringed(helper, "route_cursed", "enigmaticlegacy:cursed_ring", 10000);
        IronsProxyCastDriver.setIronsMana(player, 0.0f);
        float arsBefore = BridgeManager.getNativeArsBridge().getMana(player);
        int lpBefore = SanctifiedLegacyCompat.getBloodMagicLP(player);
        ImportedGlyphGameTests.cast(helper, player, new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(HEAL, 1)));
        helper.assertTrue(player.getHealth() > 10.0f, "the imported Heal runs with no Iron's mana: admission did not demand it");
        helper.assertTrue(IronsProxyCastDriver.ironsMana(player) == 0.0f, "no Iron's mana was spent");
        helper.assertTrue(BridgeManager.getNativeArsBridge().getMana(player) == arsBefore, "no Ars mana was spent: the ring pays the Ars leg in LP");
        double imported = importedLp(player);
        int rawSpent = lpBefore - SanctifiedLegacyCompat.getBloodMagicLP(player);
        helper.assertTrue(imported > 0, "the imported leg paid LP: " + PaymentTimeline.entries(player.getUUID()));
        helper.assertTrue(Math.abs(rawSpent - allLp(player)) < 1.0e-6, "every LP movement is a recorded leg: raw " + rawSpent
            + ", recorded " + allLp(player) + "\n" + PaymentTimeline.entries(player.getUUID()));
        // The native cast of the same spell, same caster, same ring.
        player.setHealth(10.0f);
        IronsGlyphTestSupport.clearCooldowns(player);
        int nativeBefore = SanctifiedLegacyCompat.getBloodMagicLP(player);
        helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(player, IronsProxyCastDriver.nativeBook(HEAL), 0), "native Heal starts");
        IronsProxyCastDriver.tickUntilCastEnds(player);
        int nativeSpent = nativeBefore - SanctifiedLegacyCompat.getBloodMagicLP(player);
        helper.assertTrue(player.getHealth() > 10.0f, "the native Heal runs");
        helper.assertTrue(Math.abs(nativeSpent - imported) < 1.0e-6,
            "the imported leg pays exactly the native LP: imported " + imported + ", native " + nativeSpent);
        helper.assertTrue(noOpenLegs(player), "no LP leg of this caster is left open: " + AlternativePayment.allOpen());
        helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the session closes");
        helper.succeed();
    }

    /** Enough LP for the Ars leg, not for the imported one: refused before anything is taken. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 200)
    public static void covenantLoaded_route_cursedRingInsufficientLpRefusesAndTakesNothing(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, COVENANT)) return;
        ServerPlayer player = ringed(helper, "route_short", "enigmaticlegacy:cursed_ring", 10000);
        IronsGlyphTestSupport.fundIronsPool(player, 5000.0f);
        Spell spell = new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(HEAL, 8));
        // Measure both legs of this exact cast once, with plenty of LP.
        ImportedGlyphGameTests.cast(helper, player, spell);
        double imported = importedLp(player), ars = allLp(player) - imported;
        helper.assertTrue(imported > ars + 1, "fixture: the imported leg costs more LP than the Ars leg (" + imported + " vs " + ars + ")");
        IronsGlyphTestSupport.clearCooldowns(player);
        PaymentTimeline.clear(player.getUUID());
        player.setHealth(10.0f);
        setLp(player, (int) Math.round(ars + imported) - 1);
        int lpBefore = SanctifiedLegacyCompat.getBloodMagicLP(player);
        float ironsBefore = IronsProxyCastDriver.ironsMana(player);
        ImportedGlyphGameTests.cast(helper, player, spell);
        helper.assertTrue(player.getHealth() == 10.0f, "the Heal does not run");
        helper.assertTrue(SanctifiedLegacyCompat.getBloodMagicLP(player) == lpBefore, "no LP is taken by a refused cast");
        helper.assertTrue(IronsProxyCastDriver.ironsMana(player) == ironsBefore, "and no mana: the route is LP, refused, not silently switched");
        var records = GlyphSessions.records(player.getUUID());
        helper.assertTrue(!records.isEmpty() && records.get(records.size() - 1).outcome().contains("message.ars_n_spells.cast.insufficient"),
            "the caster is told LP is short: " + records);
        helper.assertTrue(noOpenLegs(player), "no LP leg of this caster is left open: " + AlternativePayment.allOpen());
        helper.succeed();
    }

    /** The native fallback policy: with too little LP for the imported leg it pays routed mana instead, as a native cast falls back. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 200)
    public static void covenantLoaded_route_nativeFallbackPaysManaWhenLpIsShort(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, COVENANT)) return;
        ServerPlayer player = ringed(helper, "route_fallback", "enigmaticlegacy:cursed_ring", 10000);
        // Iron's clamps its pool to the max-mana attribute (100 by default); Heal VIII needs more.
        IronsGlyphTestSupport.fundIronsPool(player, 5000.0f);
        Spell spell = new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(HEAL, 8));
        ImportedGlyphGameTests.cast(helper, player, spell);
        double ars = allLp(player) - importedLp(player);
        IronsGlyphTestSupport.clearCooldowns(player);
        PaymentTimeline.clear(player.getUUID());
        player.setHealth(10.0f);
        TestConfig.set(AnsConfig.PAYMENT_OPEN_FAILURE_POLICY, "native_fallback");
        AnsConfig.refreshResolvedPolicies();
        setLp(player, (int) Math.round(ars));
        float ironsBefore = IronsProxyCastDriver.ironsMana(player);
        ImportedGlyphGameTests.cast(helper, player, spell);
        TestConfig.set(AnsConfig.PAYMENT_OPEN_FAILURE_POLICY, "refuse");
        AnsConfig.refreshResolvedPolicies();
        helper.assertTrue(player.getHealth() > 10.0f, "the Heal runs on the fallback: " + GlyphSessions.records(player.getUUID())
            + "\n" + PaymentTimeline.entries(player.getUUID()));
        helper.assertTrue(importedLp(player) == 0, "the imported leg took no LP");
        helper.assertTrue(IronsProxyCastDriver.ironsMana(player) < ironsBefore, "it paid routed mana instead");
        helper.assertTrue(noOpenLegs(player), "no LP leg of this caster is left open: " + AlternativePayment.allOpen());
        helper.succeed();
    }

    /** Virtue Ring with the aura system on: Covenant takes ambient aura, ANS takes no mana, and admission demands none. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 200)
    public static void covenantLoaded_route_virtueRingImportedGlyphPaysCovenantAuraNotMana(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, COVENANT)) return;
        ServerPlayer player = ringed(helper, "route_virtue", "covenant_of_the_seven:virtue_ring", 10000);
        IronsProxyCastDriver.setIronsMana(player, 0.0f);
        int auraBefore = SanctifiedLegacyCompat.getCovenantAura(player);
        int lpBefore = SanctifiedLegacyCompat.getBloodMagicLP(player);
        ImportedGlyphGameTests.cast(helper, player, new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(HEAL, 1)));
        helper.assertTrue(player.getHealth() > 10.0f, "the imported Heal runs with no Iron's mana");
        helper.assertTrue(IronsProxyCastDriver.ironsMana(player) == 0.0f, "no Iron's mana was spent");
        helper.assertTrue(SanctifiedLegacyCompat.getCovenantAura(player) < auraBefore, "Covenant took aura for it");
        helper.assertTrue(SanctifiedLegacyCompat.getBloodMagicLP(player) == lpBefore, "and no LP");
        helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the session closes");
        helper.succeed();
    }

    /** A charged glyph under the Cursed Ring: the admission leg is handed back and Iron's own pipeline takes the LP once. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 200)
    public static void covenantLoaded_route_cursedRingChargedGlyphPaysLpOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, COVENANT)) return;
        ServerPlayer player = ringed(helper, "route_charged", "enigmaticlegacy:cursed_ring", 10000);
        IronsProxyCastDriver.setIronsMana(player, 0.0f);
        TouchRegressionGameTests.lookAtSky(player);
        int lpBefore = SanctifiedLegacyCompat.getBloodMagicLP(player);
        ImportedGlyphGameTests.cast(helper, player, new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(FIREBALL, 1)));
        helper.assertTrue(IronsGlyphTestSupport.casting(player), "the imported Fireball charges with no Iron's mana");
        IronsProxyCastDriver.tickUntilCastEnds(player);
        int spent = lpBefore - SanctifiedLegacyCompat.getBloodMagicLP(player);
        helper.assertTrue(Math.abs(spent - allLp(player)) < 1.0e-6, "every LP movement is a recorded leg: raw " + spent
            + ", recorded " + allLp(player) + "\n" + PaymentTimeline.entries(player.getUUID()));
        long releases = PaymentTimeline.entries(player.getUUID()).stream().filter(e -> e.kind() == PaymentTimeline.Kind.REFUND).count();
        helper.assertTrue(releases >= 1, "the admission leg was handed back before Iron's pipeline priced the release\n"
            + PaymentTimeline.entries(player.getUUID()));
        helper.assertTrue(IronsProxyCastDriver.ironsMana(player) == 0.0f, "no Iron's mana was spent");
        helper.assertTrue(noOpenLegs(player), "no LP leg of this caster is left open: " + AlternativePayment.allOpen());
        helper.succeed();
    }

    // ---- Audit ANS-001: the captured route is authorized again at every execution ------------------

    private static final int DELAY_WAIT_TICKS = 100;

    /** Positive control for the delayed cases below: the ring stays on, the delayed Heal runs and Covenant takes the aura. */
    @GameTest(template = "platform", batch = BATCH + "_keep", timeoutTicks = 300)
    public static void covenantLoaded_route_delayedVirtueGlyphKeptRingPaysAura(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, COVENANT)) return;
        ServerPlayer player = ringed(helper, "route_delay_keep", "covenant_of_the_seven:virtue_ring", 10000);
        IronsProxyCastDriver.setIronsMana(player, 0.0f);
        int auraBefore = SanctifiedLegacyCompat.getCovenantAura(player);
        int lpBefore = SanctifiedLegacyCompat.getBloodMagicLP(player);
        ImportedGlyphGameTests.cast(helper, player, delayedHeal());
        helper.assertTrue(player.getHealth() == 10.0f, "the Heal is delayed");
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(player.getHealth() > 10.0f, "the delayed Heal runs with the ring kept"))
            .thenExecute(() -> {
                helper.assertTrue(SanctifiedLegacyCompat.getCovenantAura(player) < auraBefore, "Covenant took aura for the delayed Heal");
                helper.assertTrue(SanctifiedLegacyCompat.getBloodMagicLP(player) == lpBefore, "and no LP");
                helper.assertTrue(IronsProxyCastDriver.ironsMana(player) == 0.0f, "and no mana");
            }).thenSucceed();
    }

    /** The audit's exact reproduction, inverted: removing the Virtue Ring after admission must not make the delayed Heal free. */
    @GameTest(template = "platform", batch = BATCH + "_off", timeoutTicks = 300)
    public static void covenantLoaded_route_delayedVirtueGlyphRefusedAfterRingRemoved(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, COVENANT)) return;
        ServerPlayer player = ringed(helper, "route_delay_off", "covenant_of_the_seven:virtue_ring", 10000);
        IronsProxyCastDriver.setIronsMana(player, 0.0f);
        ImportedGlyphGameTests.cast(helper, player, delayedHeal());
        helper.assertTrue(player.getHealth() == 10.0f, "the Heal is delayed");
        ringSlot(player).setStackInSlot(0, ItemStack.EMPTY);
        SanctifiedLegacyCompat.clearCacheFor(player.getUUID());
        expectRefusedChild(helper, player, () -> {});
    }

    /** Turning the aura integration off after admission: Covenant no longer services the route, so the child is refused. */
    @GameTest(template = "platform", batch = BATCH + "_cfg", timeoutTicks = 300)
    public static void covenantLoaded_route_delayedVirtueGlyphRefusedAfterAuraDisabled(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, COVENANT)) return;
        ServerPlayer player = ringed(helper, "route_delay_cfg", "covenant_of_the_seven:virtue_ring", 10000);
        IronsProxyCastDriver.setIronsMana(player, 0.0f);
        ImportedGlyphGameTests.cast(helper, player, delayedHeal());
        helper.assertTrue(player.getHealth() == 10.0f, "the Heal is delayed");
        TestConfig.set(AnsConfig.ENABLE_VIRTUE_AURA_SYSTEM, false);
        expectRefusedChild(helper, player, () -> TestConfig.set(AnsConfig.ENABLE_VIRTUE_AURA_SYSTEM, true));
    }

    /** A Cursed Ring added beside the Virtue Ring cancels it, natively and here: the aura child is refused, not repriced. */
    @GameTest(template = "platform", batch = BATCH + "_both", timeoutTicks = 300)
    public static void covenantLoaded_route_delayedVirtueGlyphRefusedAfterCursedRingAdded(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, COVENANT)) return;
        ServerPlayer player = ringed(helper, "route_delay_both", "covenant_of_the_seven:virtue_ring", 10000);
        IronsProxyCastDriver.setIronsMana(player, 0.0f);
        var slot = ringSlot(player);
        helper.assertTrue(slot.getSlots() >= 2, "fixture: two ring slots, got " + slot.getSlots());
        ImportedGlyphGameTests.cast(helper, player, delayedHeal());
        helper.assertTrue(player.getHealth() == 10.0f, "the Heal is delayed");
        slot.setStackInSlot(1, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation("enigmaticlegacy:cursed_ring"))));
        SanctifiedLegacyCompat.clearCacheFor(player.getUUID());
        expectRefusedChild(helper, player, () -> slot.setStackInSlot(1, ItemStack.EMPTY));
    }

    /** Equipping Virtue after a mana admission: no silent move to aura, no aura debit and no zero-cost child. */
    @GameTest(template = "platform", batch = BATCH + "_mana", timeoutTicks = 300)
    public static void covenantLoaded_route_virtueEquippedAfterManaAdmissionIsRefusedNotFree(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, COVENANT)) return;
        ServerPlayer player = ringed(helper, "route_delay_mana", null, 10000);
        IronsGlyphTestSupport.fundIronsPool(player, 5000.0f);
        IronsProxyCastDriver.setIronsMana(player, 1000.0f);
        ImportedGlyphGameTests.cast(helper, player, delayedHeal());
        helper.assertTrue(player.getHealth() == 10.0f, "the Heal is delayed");
        // After the root paid (and the child's share was held, if anything holds it).
        float manaBefore = IronsProxyCastDriver.ironsMana(player);
        ringSlot(player).setStackInSlot(0, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
            new ResourceLocation("covenant_of_the_seven:virtue_ring"))));
        SanctifiedLegacyCompat.clearCacheFor(player.getUUID());
        int auraBefore = SanctifiedLegacyCompat.getCovenantAura(player);
        helper.runAfterDelay(DELAY_WAIT_TICKS, () -> {
            helper.assertTrue(player.getHealth() == 10.0f, "the mana-admitted Heal must not run after its route changed");
            helper.assertTrue(SanctifiedLegacyCompat.getCovenantAura(player) >= auraBefore, "no aura was taken for a mana admission");
            helper.assertTrue(IronsProxyCastDriver.ironsMana(player) + 1.0e-3 >= manaBefore,
                "nothing more was spent for the refused child: " + IronsProxyCastDriver.ironsMana(player) + " of " + manaBefore);
            assertNoEffectRecorded(helper, player);
            ringSlot(player).setStackInSlot(0, ItemStack.EMPTY);
            helper.succeed();
        });
    }

    /** Projectile delivery: the ring comes off while the projectile flies; the effect on impact is refused, not free. */
    @GameTest(template = "platform", batch = BATCH + "_proj", timeoutTicks = 300)
    public static void covenantLoaded_route_projectileVirtueGlyphRefusedAfterRingRemoved(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, COVENANT)) return;
        ServerPlayer player = ringed(helper, "route_proj_off", "covenant_of_the_seven:virtue_ring", 10000);
        IronsProxyCastDriver.setIronsMana(player, 0.0f);
        var pig = TouchRegressionGameTests.pigInFront(helper);
        TouchRegressionGameTests.aimAt(player, pig.getBoundingBox().getCenter());
        // The audited projectile pattern (ImportedGlyphGameTests): Chain Lightning starts at the mob the projectile hit.
        ImportedGlyphGameTests.cast(helper, player, new Spell(com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile.INSTANCE,
            ImportedGlyphGameTests.glyph("irons_spellbooks:chain_lightning", 1)));
        ringSlot(player).setStackInSlot(0, ItemStack.EMPTY);
        SanctifiedLegacyCompat.clearCacheFor(player.getUUID());
        // After the Ars root paid its own aura at cast time: only the imported child's payment is in question.
        int auraBefore = SanctifiedLegacyCompat.getCovenantAura(player);
        helper.runAfterDelay(60, () -> {
            // The record of every execution is the oracle: pig health alone is not, since other mods in the
            // larger profiles can touch the pig (Iron's 3.16.3 with every addon: 3 damage with the effect refused).
            assertNoEffectRecorded(helper, player);
            helper.assertTrue(SanctifiedLegacyCompat.getCovenantAura(player) >= auraBefore, "no aura charged");
            helper.assertTrue(IronsProxyCastDriver.ironsMana(player) == 0.0f, "no mana charged");
            helper.succeed();
        });
    }

    /** A charged child whose ring comes off before its release: the release is refused, no mana is charged in place of the aura. */
    @GameTest(template = "platform", batch = BATCH + "_charge", timeoutTicks = 300)
    public static void covenantLoaded_route_chargedVirtueGlyphRefusedWhenRingRemovedBeforeRelease(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, COVENANT)) return;
        ServerPlayer player = ringed(helper, "route_charge_off", "covenant_of_the_seven:virtue_ring", 10000);
        IronsGlyphTestSupport.fundIronsPool(player, 5000.0f);
        IronsProxyCastDriver.setIronsMana(player, 1000.0f);
        TouchRegressionGameTests.lookAtSky(player);
        ImportedGlyphGameTests.cast(helper, player, new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(FIREBALL, 1)));
        helper.assertTrue(IronsGlyphTestSupport.casting(player), "the imported Fireball charges on the aura route");
        float manaBefore = IronsProxyCastDriver.ironsMana(player);
        ringSlot(player).setStackInSlot(0, ItemStack.EMPTY);
        SanctifiedLegacyCompat.clearCacheFor(player.getUUID());
        net.minecraft.world.phys.AABB around = player.getBoundingBox().inflate(48);
        int before = IronsGlyphTestSupport.magicProjectilesNear(player, around);
        IronsProxyCastDriver.tickUntilCastEnds(player);
        helper.assertTrue(IronsGlyphTestSupport.magicProjectilesNear(player, around) == before, "the refused release launches nothing");
        helper.assertTrue(IronsProxyCastDriver.ironsMana(player) + 1.0e-3 >= manaBefore,
            "no mana is charged in place of the aura: " + IronsProxyCastDriver.ironsMana(player) + " of " + manaBefore);
        helper.assertTrue(noOpenLegs(player), "no LP leg of this caster is left open: " + AlternativePayment.allOpen());
        helper.succeed();
    }

    private static Spell delayedHeal() {
        return new Spell(MethodSelf.INSTANCE, com.hollingsworth.arsnouveau.common.spell.effect.EffectDelay.INSTANCE,
            ImportedGlyphGameTests.glyph(HEAL, 1));
    }

    private static top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler ringSlot(ServerPlayer player) {
        return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve().orElseThrow()
            .getCurios().get("ring").getStacks();
    }

    /** After the delay: no heal, no aura, no LP, no mana, a recorded refusal and nothing left open. */
    private static void expectRefusedChild(GameTestHelper helper, ServerPlayer player, Runnable restore) {
        int auraBefore = SanctifiedLegacyCompat.getCovenantAura(player);
        int lpBefore = SanctifiedLegacyCompat.getBloodMagicLP(player);
        helper.runAfterDelay(DELAY_WAIT_TICKS, () -> {
            try {
                helper.assertTrue(player.getHealth() == 10.0f, "the delayed imported Heal must not execute for free, health " + player.getHealth());
                helper.assertTrue(SanctifiedLegacyCompat.getCovenantAura(player) >= auraBefore, "no aura was consumed");
                helper.assertTrue(SanctifiedLegacyCompat.getBloodMagicLP(player) == lpBefore, "no LP was consumed");
                helper.assertTrue(IronsProxyCastDriver.ironsMana(player) == 0.0f, "no mana was consumed");
                assertNoEffectRecorded(helper, player);
                helper.assertTrue(noOpenLegs(player), "no LP leg of this caster is left open: " + AlternativePayment.allOpen());
                helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the session closes after the refusal");
            } finally {
                restore.run();
            }
            helper.succeed();
        });
    }

    private static void assertNoEffectRecorded(GameTestHelper helper, ServerPlayer player) {
        var records = GlyphSessions.records(player.getUUID());
        helper.assertTrue(records.stream().noneMatch(r -> r.outcome().startsWith("effect_applied")),
            "no execution of this cast applied its effect: " + records);
    }

    // ---- Fixtures --------------------------------------------------------------------------------

    private static volatile boolean lpBefore, auraBefore, deathBefore;
    private static volatile String sourceBefore, policyBefore, modeBefore;

    @BeforeBatch(batch = BATCH)
    public static void before(ServerLevel level) {
        lpBefore = AnsConfig.ENABLE_LP_SYSTEM.get();
        auraBefore = AnsConfig.ENABLE_VIRTUE_AURA_SYSTEM.get();
        deathBefore = AnsConfig.DEATH_ON_INSUFFICIENT_LP.get();
        sourceBefore = AnsConfig.LP_SOURCE_MODE.get();
        policyBefore = AnsConfig.PAYMENT_OPEN_FAILURE_POLICY.get();
        modeBefore = AnsConfig.MANA_UNIFICATION_MODE.get();
        TestConfig.set(AnsConfig.ENABLE_LP_SYSTEM, true);
        TestConfig.set(AnsConfig.ENABLE_VIRTUE_AURA_SYSTEM, true);
        TestConfig.set(AnsConfig.DEATH_ON_INSUFFICIENT_LP, false);
        TestConfig.set(AnsConfig.LP_SOURCE_MODE, "BLOOD_MAGIC_ONLY");
        TestConfig.set(AnsConfig.PAYMENT_OPEN_FAILURE_POLICY, "refuse");
        TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, "iss_primary");
        AnsConfig.refreshResolvedPolicies();
        BridgeManager.refreshMode();
    }

    // Audit ANS-001: one batch per aura-reading test. Covenant's aura is Nature's Aura sampled over a
    // 35-block radius, so concurrent tests in one batch read and drain the same ambient pool, and the
    // aura-disabled case toggles a global.
    @BeforeBatch(batch = BATCH + "_keep") public static void beforeKeep(ServerLevel level) { before(level); }
    @AfterBatch(batch = BATCH + "_keep") public static void afterKeep(ServerLevel level) { after(level); }
    @BeforeBatch(batch = BATCH + "_off") public static void beforeOff(ServerLevel level) { before(level); }
    @AfterBatch(batch = BATCH + "_off") public static void afterOff(ServerLevel level) { after(level); }
    @BeforeBatch(batch = BATCH + "_cfg") public static void beforeCfg(ServerLevel level) { before(level); }
    @AfterBatch(batch = BATCH + "_cfg") public static void afterCfg(ServerLevel level) { after(level); }
    @BeforeBatch(batch = BATCH + "_both") public static void beforeBoth(ServerLevel level) { before(level); }
    @AfterBatch(batch = BATCH + "_both") public static void afterBoth(ServerLevel level) { after(level); }
    @BeforeBatch(batch = BATCH + "_mana") public static void beforeMana(ServerLevel level) { before(level); }
    @AfterBatch(batch = BATCH + "_mana") public static void afterMana(ServerLevel level) { after(level); }
    @BeforeBatch(batch = BATCH + "_proj") public static void beforeProj(ServerLevel level) { before(level); }
    @AfterBatch(batch = BATCH + "_proj") public static void afterProj(ServerLevel level) { after(level); }
    @BeforeBatch(batch = BATCH + "_charge") public static void beforeCharge(ServerLevel level) { before(level); }
    @AfterBatch(batch = BATCH + "_charge") public static void afterCharge(ServerLevel level) { after(level); }

    @AfterBatch(batch = BATCH)
    public static void after(ServerLevel level) {
        if (sourceBefore == null) return;
        TestConfig.set(AnsConfig.ENABLE_LP_SYSTEM, lpBefore);
        TestConfig.set(AnsConfig.ENABLE_VIRTUE_AURA_SYSTEM, auraBefore);
        TestConfig.set(AnsConfig.DEATH_ON_INSUFFICIENT_LP, deathBefore);
        TestConfig.set(AnsConfig.LP_SOURCE_MODE, sourceBefore);
        TestConfig.set(AnsConfig.PAYMENT_OPEN_FAILURE_POLICY, policyBefore);
        TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, modeBefore);
        AnsConfig.refreshResolvedPolicies();
        BridgeManager.refreshMode();
    }

    private static ServerPlayer ringed(GameTestHelper helper, String name, String ring, int lp) {
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, name);
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(10.0f);
        TouchRegressionGameTests.fundArs(player);
        IronsGlyphTestSupport.clearCooldowns(player);
        var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve().orElseThrow();
        var slot = curios.getCurios().get("ring");
        if (slot == null) throw new IllegalStateException("Pinned Covenant ring slot missing");
        for (int i = 0; i < slot.getStacks().getSlots(); i++) slot.getStacks().setStackInSlot(i, ItemStack.EMPTY);
        if (ring != null)
            slot.getStacks().setStackInSlot(0, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(ring))));
        SanctifiedLegacyCompat.clearCacheFor(player.getUUID());
        helper.assertTrue(ring == null ? !SanctifiedLegacyCompat.isWearingVirtueRing(player) && !SanctifiedLegacyCompat.isWearingCursedRing(player)
            : ring.endsWith("virtue_ring") ? SanctifiedLegacyCompat.isWearingVirtueRing(player)
            : SanctifiedLegacyCompat.isWearingCursedRing(player), "the ring is worn");
        setLp(player, lp);
        PaymentTimeline.enableForTesting(true);
        PaymentTimeline.clear(player.getUUID());
        return player;
    }

    private static boolean noOpenLegs(ServerPlayer player) {
        return AlternativePayment.allOpen().stream().noneMatch(leg -> leg.playerId().equals(player.getUUID()));
    }

    private static void setLp(ServerPlayer player, int lp) {
        try {
            Object network = Class.forName("wayoftime.bloodmagic.util.helper.NetworkHelper").getMethod("getSoulNetwork", java.util.UUID.class)
                .invoke(null, player.getUUID());
            network.getClass().getMethod("setCurrentEssence", int.class).invoke(network, lp);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    /** Net LP moved by the imported glyphs' legs (their markers), from the timeline. */
    private static double importedLp(ServerPlayer player) {
        List<PaymentTimeline.Entry> entries = PaymentTimeline.entries(player.getUUID());
        Set<String> imported = new HashSet<>();
        for (PaymentTimeline.Entry e : entries) if (e.label().startsWith("glyph-lp:")) imported.add("alt:" + e.label().substring(9));
        return netLp(entries, imported);
    }

    /** Net LP moved by every alternative leg, from the timeline. */
    private static double allLp(ServerPlayer player) {
        return netLp(PaymentTimeline.entries(player.getUUID()), null);
    }

    private static double netLp(List<PaymentTimeline.Entry> entries, Set<String> only) {
        double net = 0;
        for (PaymentTimeline.Entry e : entries) {
            if (!e.label().startsWith("alt:") || (only != null && !only.contains(e.label()))) continue;
            if (!e.detail().contains("moved=" + ResourceUnit.LP + ":")) continue;
            double moved = Double.parseDouble(e.detail().substring(e.detail().indexOf("moved=LP:") + 9).trim());
            if (e.kind() == PaymentTimeline.Kind.DEBIT) net += moved;
            else if (e.kind() == PaymentTimeline.Kind.REFUND) net -= moved;
        }
        return net;
    }
}
