package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.event.ManaRegenCalcEvent;
import com.hollingsworth.arsnouveau.api.spell.AbstractCastMethod;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAOE;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectBurst;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectDelay;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.casting.PaymentTimeline;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.GlyphMappings;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.runtime.GlyphOutcome;
import com.otectus.arsnspells.glyph.runtime.GlyphSessions;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The 3.4.1 review's casting acceptance scenarios on NeoForge, observed in the world and in the
 * {@link PaymentTimeline}: a persistent effect's whole life is billed once (CAST-01); work admitted
 * under one policy refuses at completion under a stricter one, and a place-targeting glyph outlives
 * the entity that stood there (CAST-03); a native failure before payment is an unpaid refusal, one
 * after the effect began stays paid with its cooldown, and a cancelled channel keeps the cooldown its
 * paid pulses earned (CAST-04); a fanned-out charged glyph admits no more pending executions than the
 * cast's budget (CAST-05). Tests that change a global own their batch and restore it there.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class CastReviewGameTests {
    private static final String FIREBOLT = "irons_spellbooks:firebolt";
    private static final String FIRE_BREATH = "irons_spellbooks:fire_breath";
    private static final String MAGMA_BOMB = "irons_spellbooks:magma_bomb";
    private static final String POISON_SPLASH = "irons_spellbooks:poison_splash";
    private static final String SLOW = "irons_spellbooks:slow";
    private static final String BUDGET_BATCH = "ans_review_budget";
    private static final String BUDGET_SURVIVAL_BATCH = "ans_review_budget_survival";
    private static final String DISABLE_BATCH = "ans_review_disable_channel";
    private static final String MAX_BATCH = "ans_review_max_charge";
    private static final String HEAL = "irons_spellbooks:heal";
    private static final String MAX_DELAY_BATCH = "ans_review_max_delay";
    private static final String RULES_BATCH = "ans_review_rules";
    private static final String BORDER_BATCH = "ans_review_border";
    private static final String MODE_ISS = "ans_review_lifetime_iss";
    private static final String MODE_ARS = "ans_review_lifetime_ars";
    private static final String MODE_HYBRID = "ans_review_lifetime_hybrid";
    private static final String MODE_SEPARATE = "ans_review_lifetime_separate";
    private static final String MODE_DISABLED = "ans_review_lifetime_disabled";
    private static final int AFTERMATH = 40;

    private CastReviewGameTests() {}

    // ---- CAST-04: failures before and after the effect boundary -------------------------------

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void ironsLoaded_failure_beforePaymentIsAnUnpaidRefusal(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "review_fail_pre");
        Pig pig = sturdyPig(helper, new Vec3(3.5, 2.0, 2.5));
        ImportedGlyphGameTests.aim(player, pig.getBoundingBox().getCenter());
        AutoCloseable fault = CastReviewIronsSupport.arm(player, CastReviewIronsSupport.Fault.CAST_EVENT, FIREBOLT, 1);
        try {
            ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodTouch.INSTANCE, ImportedGlyphGameTests.glyph("firebolt", 1)));
        } finally {
            CastReviewIronsSupport.disarm(fault);
        }
        helper.assertTrue(outcome(player, FIREBOLT, GlyphOutcome.REJECTED, "refused.failed"),
            "a failure before payment is reported as a refusal: " + GlyphSessions.outcomes(player.getUUID()));
        helper.assertTrue(!IronsProxyCastDriver.onNativeCooldown(player, FIREBOLT), "no cooldown for a cast that never began");
        assertNetImportedDebit(helper, player, 0.0);
        helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the session closes");
        helper.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void ironsLoaded_failure_afterTheEffectBeganStaysPaidWithItsCooldown(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "review_fail_post");
        Pig pig = sturdyPig(helper, new Vec3(3.5, 2.0, 2.5));
        ImportedGlyphGameTests.aim(player, pig.getBoundingBox().getCenter());
        double price = ImportedGlyphGameTests.glyphPrice(player, "firebolt", 1);
        AutoCloseable fault = CastReviewIronsSupport.arm(player, CastReviewIronsSupport.Fault.SPAWN, FIREBOLT, 1);
        try {
            ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodTouch.INSTANCE, ImportedGlyphGameTests.glyph("firebolt", 1)));
        } finally {
            CastReviewIronsSupport.disarm(fault);
        }
        helper.assertTrue(outcome(player, FIREBOLT, GlyphOutcome.FAILED_AFTER_EFFECT, "refused.failed"),
            "a failure after the effect began is FAILED_AFTER_EFFECT: " + GlyphSessions.outcomes(player.getUUID()));
        helper.assertTrue(IronsProxyCastDriver.onNativeCooldown(player, FIREBOLT), "the cooldown applies: it cannot be retried for free");
        helper.assertTrue(price > 0, "fixture: Firebolt has a price");
        assertNetImportedDebit(helper, player, price);
        helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the session closes");
        helper.succeed();
    }

    /**
     * 3.4.1 review CAST-04: a native {@link LinkageError} (an addon built against another Iron's build) is
     * contained like any failure, for an instant execution and for a charge's release, which used to let
     * it escape into the server tick and crash the server.
     */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void ironsLoaded_failure_aLinkageErrorAfterTheEffectBeganIsContained(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "review_fail_link");
        Pig pig = sturdyPig(helper, new Vec3(3.5, 2.0, 2.5));
        ImportedGlyphGameTests.aim(player, pig.getBoundingBox().getCenter());
        double price = ImportedGlyphGameTests.glyphPrice(player, "firebolt", 1);
        AutoCloseable fault = CastReviewIronsSupport.arm(player, CastReviewIronsSupport.Fault.SPAWN_LINKAGE, FIREBOLT, 1);
        try {
            ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodTouch.INSTANCE, ImportedGlyphGameTests.glyph("firebolt", 1)));
        } finally {
            CastReviewIronsSupport.disarm(fault);
        }
        helper.assertTrue(outcome(player, FIREBOLT, GlyphOutcome.FAILED_AFTER_EFFECT, "refused.failed"),
            "a linkage failure after the effect began is FAILED_AFTER_EFFECT: " + GlyphSessions.outcomes(player.getUUID()));
        helper.assertTrue(IronsProxyCastDriver.onNativeCooldown(player, FIREBOLT), "the cooldown applies");
        assertNetImportedDebit(helper, player, price);
        helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the session closes");
        helper.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_failure_aLinkageErrorAtAChargesReleaseIsContained(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "review_link_charge");
        ImportedGlyphGameTests.aim(player, player.getEyePosition().add(0, 5, 0));
        AutoCloseable fault = CastReviewIronsSupport.arm(player, CastReviewIronsSupport.Fault.OWNED_PROJECTILE_LINKAGE, MAGMA_BOMB, 1);
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph("magma_bomb", 1)));
        helper.assertTrue(GlyphSessions.continuationsFor(player.getUUID()) == 1, "the Magma Bomb charges");
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(GlyphSessions.continuationsFor(player.getUUID()) == 0, "the charge releases"))
            .thenExecute(() -> {
                CastReviewIronsSupport.disarm(fault);
                helper.assertTrue(outcome(player, MAGMA_BOMB, GlyphOutcome.FAILED_AFTER_EFFECT, "refused.failed"),
                    "the release's linkage failure is contained as FAILED_AFTER_EFFECT: " + GlyphSessions.outcomes(player.getUUID()));
                helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the session closes");
            })
            .thenSucceed();
    }

    /** The first pulse runs and is paid; the second pulse's native event throws: the channel ends with its cooldown. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_failure_laterChannelPulseKeepsThePaidPulseAndTheCooldown(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "review_fail_pulse");
        AutoCloseable fault = CastReviewIronsSupport.arm(player, CastReviewIronsSupport.Fault.CAST_EVENT, FIRE_BREATH, 2);
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph("fire_breath", 1)));
        helper.assertTrue(GlyphSessions.continuationsFor(player.getUUID()) == 1, "the channel runs");
        helper.startSequence()
            .thenExecuteAfter(30, () -> {
                CastReviewIronsSupport.disarm(fault);
                helper.assertTrue(GlyphSessions.continuationsFor(player.getUUID()) == 0, "the failed channel ended");
                helper.assertTrue(outcome(player, FIRE_BREATH, GlyphOutcome.FAILED_AFTER_EFFECT, "failed after"),
                    "the failure is recorded after the paid pulse: " + GlyphSessions.outcomes(player.getUUID()));
                helper.assertTrue(IronsProxyCastDriver.onNativeCooldown(player, FIRE_BREATH), "the paid pulse earned the cooldown");
                long effects = PaymentTimeline.entries(player.getUUID()).stream()
                    .filter(e -> e.kind() == PaymentTimeline.Kind.NATIVE_EFFECT && e.label().equals(FIRE_BREATH)).count();
                helper.assertTrue(effects == 1, "exactly the first pulse ran: " + effects);
                helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the session closes");
            })
            .thenSucceed();
    }

    @GameTest(template = "platform", batch = DISABLE_BATCH, timeoutTicks = 200)
    public static void ironsLoaded_cancel_disablingGlyphsAfterAPaidPulseKeepsTheCooldown(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "review_off_pulse");
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph("fire_breath", 1)));
        long[] effectsAtStop = {-1};
        helper.startSequence()
            .thenExecuteAfter(25, () -> {
                helper.assertTrue(GlyphSessions.continuationsFor(player.getUUID()) == 1, "the channel is running");
                TestConfig.set(AnsConfig.ENABLE_IRON_GLYPHS, false);
                GlyphBehavior.onConfigChanged();
            })
            .thenExecuteAfter(3, () -> {
                helper.assertTrue(GlyphSessions.continuationsFor(player.getUUID()) == 0, "turning imported glyphs off stops the channel");
                helper.assertTrue(IronsProxyCastDriver.onNativeCooldown(player, FIRE_BREATH), "the paid pulses keep their cooldown");
                effectsAtStop[0] = nativeEffects(player, FIRE_BREATH);
            })
            .thenExecuteAfter(25, () -> helper.assertTrue(nativeEffects(player, FIRE_BREATH) == effectsAtStop[0],
                "no pulse runs after the stop"))
            .thenSucceed();
    }

    @AfterBatch(batch = DISABLE_BATCH)
    public static void afterDisable(ServerLevel level) {
        TestConfig.set(AnsConfig.ENABLE_IRON_GLYPHS, true);
        GlyphBehavior.rebuild();
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_cancel_logoutAfterAPaidPulseKeepsTheCooldown(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "review_out_pulse");
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph("fire_breath", 1)));
        helper.startSequence()
            .thenExecuteAfter(25, () -> NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player)))
            .thenExecuteAfter(2, () -> {
                helper.assertTrue(GlyphSessions.continuationsFor(player.getUUID()) == 0, "logging out ends the channel");
                helper.assertTrue(IronsProxyCastDriver.onNativeCooldown(player, FIRE_BREATH), "the paid pulses keep their cooldown");
            })
            .thenSucceed();
    }

    // ---- CAST-05: the emission budget reserves pending charges --------------------------------

    @GameTest(template = "platform", batch = BUDGET_BATCH, timeoutTicks = 200)
    public static void ironsLoaded_budget_burstFanOutAdmitsOnePendingChargeInCreative(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        fanOut(helper, "review_budget_cr", GameType.CREATIVE);
    }

    @GameTest(template = "platform", batch = BUDGET_SURVIVAL_BATCH, timeoutTicks = 200)
    public static void ironsLoaded_budget_burstFanOutAdmitsOnePendingChargeInSurvival(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        fanOut(helper, "review_budget_sv", GameType.SURVIVAL);
    }

    @AfterBatch(batch = BUDGET_BATCH)
    public static void afterBudget(ServerLevel level) { restoreBudget(); }

    @AfterBatch(batch = BUDGET_SURVIVAL_BATCH)
    public static void afterBudgetSurvival(ServerLevel level) { restoreBudget(); }

    private static volatile Integer budgetBefore;

    private static void restoreBudget() {
        if (budgetBefore != null) TestConfig.set(AnsConfig.MAX_FOREIGN_EMISSIONS_PER_CAST, budgetBefore);
        budgetBefore = null;
        GlyphBehavior.rebuild();
    }

    /** Touch a pig, Burst to three pigs, then a charged Slow: with a budget of one, one charge is admitted. */
    private static void fanOut(GameTestHelper helper, String name, GameType mode) {
        if (budgetBefore == null) budgetBefore = AnsConfig.MAX_FOREIGN_EMISSIONS_PER_CAST.get();
        TestConfig.set(AnsConfig.MAX_FOREIGN_EMISSIONS_PER_CAST, 1);
        GlyphBehavior.rebuild();
        helper.assertTrue(GlyphBehavior.current().settings().maxForeignEmissions() == 1, "the budget is one");
        ServerPlayer player = caster(helper, name);
        player.setGameMode(mode);
        List<Pig> pigs = List.of(sturdyPig(helper, new Vec3(3.5, 2.0, 2.5)), sturdyPig(helper, new Vec3(3.5, 2.0, 3.5)),
            sturdyPig(helper, new Vec3(4.5, 2.0, 2.5)));
        ImportedGlyphGameTests.aim(player, pigs.get(0).getBoundingBox().getCenter());
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodTouch.INSTANCE, EffectBurst.INSTANCE, AugmentAOE.INSTANCE,
            AugmentAOE.INSTANCE, ImportedGlyphGameTests.glyph("slow", 1)));
        int scheduled = GlyphSessions.continuationsFor(player.getUUID());
        long refused = GlyphSessions.outcomes(player.getUUID()).stream()
            .filter(o -> o.outcome() == GlyphOutcome.REJECTED && o.detail().contains("emissions")).count();
        helper.assertTrue(scheduled == 1, "a budget of one admits one pending charge, got " + scheduled + ": " + GlyphSessions.outcomes(player.getUUID()));
        helper.assertTrue(refused >= 1, "the other fanned-out executions are refused by the budget: " + GlyphSessions.outcomes(player.getUUID()));
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(GlyphSessions.continuationsFor(player.getUUID()) == 0, "the charge completes"))
            .thenExecute(() -> {
                long slowed = pigs.stream().filter(p -> ImportedGlyphGameTests.hasEffect(p, "irons_spellbooks:slowed")).count();
                helper.assertTrue(slowed == 1, "exactly one native execution ran, slowed " + slowed);
                if (mode == GameType.SURVIVAL)
                    assertNetImportedDebit(helper, player, ImportedGlyphGameTests.glyphPrice(player, "slow", 1));
                helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the session closes");
            })
            .thenSucceed();
    }

    // ---- CAST-03: policy at completion, and place-targeting glyphs ------------------------------

    @GameTest(template = "platform", batch = MAX_BATCH, timeoutTicks = 200)
    public static void ironsLoaded_policy_chargeAboveALoweredMaximumRefusesAtCompletion(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "review_max_charge");
        Pig pig = sturdyPig(helper, new Vec3(3.5, 2.0, 2.5));
        ImportedGlyphGameTests.aim(player, pig.getBoundingBox().getCenter());
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodTouch.INSTANCE, ImportedGlyphGameTests.glyph("slow", 1),
            AugmentAmplify.INSTANCE, AugmentAmplify.INSTANCE));
        helper.assertTrue(GlyphSessions.continuationsFor(player.getUUID()) == 1, "Slow III charges");
        maxBefore = Glyph341IronsSupport.setMaxLevel(SLOW, 2);
        GlyphBehavior.rebuild();
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(GlyphSessions.continuationsFor(player.getUUID()) == 0, "the charge ends"))
            .thenExecute(() -> {
                helper.assertTrue(!ImportedGlyphGameTests.hasEffect(pig, "irons_spellbooks:slowed"), "no Slow above the lowered maximum");
                helper.assertTrue(outcome(player, SLOW, GlyphOutcome.REJECTED, "above_max_now"),
                    "refused for the maximum now: " + GlyphSessions.outcomes(player.getUUID()));
                assertNetImportedDebit(helper, player, 0.0);
            })
            .thenSucceed();
    }

    private static volatile int maxBefore = -1;

    @AfterBatch(batch = MAX_BATCH)
    public static void afterMax(ServerLevel level) {
        if (maxBefore > 0 && IronsCompat.isLoaded()) {
            Glyph341IronsSupport.setMaxLevel(SLOW, maxBefore);
            GlyphBehavior.rebuild();
        }
        maxBefore = -1;
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_policy_deadEntityTargetRefusesAtCompletion(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "review_dead_charge");
        Pig pig = sturdyPig(helper, new Vec3(3.5, 2.0, 2.5));
        ImportedGlyphGameTests.aim(player, pig.getBoundingBox().getCenter());
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodTouch.INSTANCE, ImportedGlyphGameTests.glyph("slow", 1)));
        pig.kill();
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(GlyphSessions.continuationsFor(player.getUUID()) == 0, "the charge ends"))
            .thenExecute(() -> {
                helper.assertTrue(outcome(player, SLOW, GlyphOutcome.REJECTED, "target_gone"),
                    "an entity-targeting charge refuses a dead target: " + GlyphSessions.outcomes(player.getUUID()));
                assertNetImportedDebit(helper, player, 0.0);
            })
            .thenSucceed();
    }

    /** A place-targeting glyph acts at the place it hit, which outlives the entity that stood there. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_policy_positionGlyphOutlivesTheEntityItHit(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "review_place");
        Pig pig = sturdyPig(helper, new Vec3(3.5, 2.0, 2.5));
        ImportedGlyphGameTests.aim(player, pig.getBoundingBox().getCenter());
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodTouch.INSTANCE, ImportedGlyphGameTests.glyph("poison_splash", 1)));
        pig.kill();
        AABB around = new AABB(player.blockPosition()).inflate(16);
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(outcome(player, POISON_SPLASH, GlyphOutcome.EFFECT_APPLIED, ""),
                "Poison Splash completes at the place: " + GlyphSessions.outcomes(player.getUUID())))
            .thenExecute(() -> helper.assertTrue(CastReviewIronsSupport.areasOwnedBy(player, around) >= 1, "the poison cloud exists"))
            .thenSucceed();
    }


    // ---- CAST-03 through a Delay: the Forge scenarios, against NeoForge's resolve-time checks ----

    /** Admitted at level 3 (base 1 and two Amplify), the maximum drops to 2 before the Delay expires. */
    @GameTest(template = "platform", batch = MAX_DELAY_BATCH, timeoutTicks = 200)
    public static void ironsLoaded_policy_delayedEffectAboveALoweredMaximumRefusesAndRefunds(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "review_max_delay");
        player.setHealth(6.0f);
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodSelf.INSTANCE, EffectDelay.INSTANCE,
            ImportedGlyphGameTests.glyph("heal", 1), AugmentAmplify.INSTANCE, AugmentAmplify.INSTANCE));
        helper.assertTrue(!GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "admitted at level 3");
        delayMaxBefore = Glyph341IronsSupport.setMaxLevel(HEAL, 2);
        GlyphBehavior.rebuild();
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the session closes"))
            .thenExecute(() -> {
                helper.assertTrue(player.getHealth() == 6.0f, "the delayed Heal at level 3 must not run under a maximum of 2");
                helper.assertTrue(outcome(player, HEAL, GlyphOutcome.REJECTED, "above_max"),
                    "refused for the maximum now: " + GlyphSessions.outcomes(player.getUUID()));
                assertNetImportedDebit(helper, player, 0.0);
            })
            .thenSucceed();
    }

    /** A Self-only rule, and then a deny rule, published while the Delay runs: each refuses the delayed Touch effect. */
    @GameTest(template = "platform", batch = RULES_BATCH, timeoutTicks = 300)
    public static void ironsLoaded_policy_newSelfOnlyAndDenyRulesRefuseDelayedEffects(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "review_rules");
        Pig pig = sturdyPig(helper, new Vec3(3.5, 2.0, 2.5));
        ImportedGlyphGameTests.aim(player, pig.getBoundingBox().getCenter());
        float health = pig.getHealth();
        Spell delayed = ImportedGlyphGameTests.spell(MethodTouch.INSTANCE, EffectDelay.INSTANCE, ImportedGlyphGameTests.glyph("firebolt", 1));
        ImportedGlyphGameTests.cast(player, delayed);
        helper.assertTrue(!GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the Touch Firebolt is admitted");
        publishRule("{\"schema_version\":2,\"spell\":\"" + FIREBOLT + "\",\"adapter\":\"ars_n_spells:self\"}");
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the first session closes"))
            .thenExecute(() -> {
                helper.assertTrue(pig.getHealth() == health, "a Touch Firebolt refuses under a Self-only rule published mid-flight");
                helper.assertTrue(outcome(player, FIREBOLT, GlyphOutcome.REJECTED, "self_only"),
                    "refused as Self-only: " + GlyphSessions.outcomes(player.getUUID()));
                assertNetImportedDebit(helper, player, 0.0);
                restoreRules();
                ImportedGlyphGameTests.clearCooldowns(player);
                PaymentTimeline.clear(player.getUUID());
                GlyphSessions.clearOutcomes(player.getUUID());
                ImportedGlyphGameTests.cast(player, delayed);
                helper.assertTrue(!GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the second Touch Firebolt is admitted");
                publishRule("{\"schema_version\":2,\"spell\":\"" + FIREBOLT + "\",\"enabled\":false}");
            })
            .thenWaitUntil(() -> helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the second session closes"))
            .thenExecute(() -> {
                // A rule that disables the glyph makes Ars itself skip it (ImportedIronEffect.isEnabled), so the
                // delayed part never reaches the runtime: no effect, no execution, and the hold is returned.
                helper.assertTrue(pig.getHealth() == health, "a Firebolt disabled by a rule published mid-flight does not run");
                helper.assertTrue(nativeEffects(player, FIREBOLT) == 0, "no native Firebolt execution: " + PaymentTimeline.entries(player.getUUID()));
                assertNetImportedDebit(helper, player, 0.0);
            })
            .thenSucceed();
    }

    /** The world border moves away from the target while the Delay runs. */
    @GameTest(template = "platform", batch = BORDER_BATCH, timeoutTicks = 200)
    public static void ironsLoaded_policy_targetOutsideAMovedBorderRefuses(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "review_border");
        Pig pig = sturdyPig(helper, new Vec3(3.5, 2.0, 2.5));
        ImportedGlyphGameTests.aim(player, pig.getBoundingBox().getCenter());
        float health = pig.getHealth();
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodTouch.INSTANCE, EffectDelay.INSTANCE,
            ImportedGlyphGameTests.glyph("firebolt", 1)));
        helper.assertTrue(!GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the Touch Firebolt is admitted");
        var border = helper.getLevel().getWorldBorder();
        borderBefore = new double[]{border.getCenterX(), border.getCenterZ(), border.getSize()};
        border.setCenter(pig.getX() + 5000, pig.getZ() + 5000);
        border.setSize(16);
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the session closes"))
            .thenExecute(() -> {
                restoreBorder(helper.getLevel());
                helper.assertTrue(pig.getHealth() == health, "nothing is cast at a target outside the world border");
                helper.assertTrue(outcome(player, FIREBOLT, GlyphOutcome.REJECTED, "target_gone"),
                    "refused for its target: " + GlyphSessions.outcomes(player.getUUID()));
                assertNetImportedDebit(helper, player, 0.0);
            })
            .thenSucceed();
    }

    /** A native level bonus may still take a legal Ars level above the scroll maximum, through a Delay. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_policy_nativeBonusAboveTheMaximumStillRunsWhenTheArsLevelIsLegal(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "review_bonus");
        int max = Glyph341IronsSupport.maxLevel(HEAL);
        player.setHealth(4.0f);
        AutoCloseable probe = Glyph341IronsSupport.probe(player, 2, 0, 1.0);
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodSelf.INSTANCE, EffectDelay.INSTANCE,
            ImportedGlyphGameTests.glyph("heal", max)));
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the session closes"))
            .thenExecute(() -> {
                try {
                    helper.assertTrue(player.getHealth() > 4.0f, "the delayed Heal at the maximum runs");
                    helper.assertTrue(Glyph341IronsSupport.lastCastLevel(probe) == max + 2,
                        "with its native bonus, at level " + (max + 2) + ", got " + Glyph341IronsSupport.lastCastLevel(probe));
                } finally {
                    Glyph341IronsSupport.close(probe);
                }
            })
            .thenSucceed();
    }

    private static volatile int delayMaxBefore = -1;
    private static volatile GlyphMappings.Result rulesBefore;
    private static volatile double[] borderBefore;

    @AfterBatch(batch = MAX_DELAY_BATCH)
    public static void afterMaxDelay(ServerLevel level) {
        if (delayMaxBefore > 0 && IronsCompat.isLoaded()) {
            Glyph341IronsSupport.setMaxLevel(HEAL, delayMaxBefore);
            GlyphBehavior.rebuild();
        }
        delayMaxBefore = -1;
    }

    @BeforeBatch(batch = RULES_BATCH)
    public static void beforeRules(ServerLevel level) { rulesBefore = GlyphBehavior.mappings(); }

    @AfterBatch(batch = RULES_BATCH)
    public static void afterRules(ServerLevel level) { restoreRules(); }

    @AfterBatch(batch = BORDER_BATCH)
    public static void afterBorder(ServerLevel level) { restoreBorder(level); }

    private static void publishRule(String json) {
        GlyphMappings.Result candidate = GlyphMappings.parse(List.of(new GlyphMappings.Source(
            ResourceLocation.fromNamespaceAndPath("ans_gametest", "review_rules"), "gametest", 1000, JsonParser.parseString(json))),
            ImportedGlyphCatalog.get());
        if (!candidate.valid()) throw new IllegalStateException("test behaviour file invalid: " + candidate.errors());
        GlyphBehavior.acceptMappings(candidate);
        GlyphBehavior.rebuild();
    }

    private static void restoreRules() {
        GlyphMappings.Result saved = rulesBefore;
        if (saved == null || GlyphBehavior.mappings() == saved) return;
        GlyphBehavior.acceptMappings(saved);
        GlyphBehavior.rebuild();
    }

    private static void restoreBorder(ServerLevel level) {
        double[] saved = borderBefore;
        if (saved == null) return;
        level.getWorldBorder().setCenter(saved[0], saved[1]);
        level.getWorldBorder().setSize(saved[2]);
        borderBefore = null;
    }

    // ---- PROGRESSION-01: training only for an accepted native effect --------------------------

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void ironsLoaded_training_unaffordableFinalPriceCreditsNoTraining(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = ImportedGlyphGameTests.caster(helper, "review_training", GameType.SURVIVAL);
        player.setHealth(10.0f);
        IronsProxyCastDriver.setMaxIronsMana(player, 100.0);
        IronsProxyCastDriver.setIronsMana(player, 100.0f);
        String school = "irons_spellbooks:holy";
        int casts = castCount(player, school), affinity = affinity(player, school);
        AutoCloseable probe = Glyph341IronsSupport.probe(player, 0, 0, 1000.0);
        try {
            IronsProxyCastDriver.initiateViaSpellSelection(player, IronsProxyCastDriver.nativeBook("irons_spellbooks:heal"), 0);
            IronsProxyCastDriver.tickUntilCastEnds(player);
        } finally {
            Glyph341IronsSupport.close(probe);
        }
        helper.assertTrue(player.getHealth() == 10.0f, "the unaffordable Heal has no effect");
        helper.assertTrue(castCount(player, school) == casts, "no progression for a cast refused at its final price");
        helper.assertTrue(affinity(player, school) == affinity, "no affinity for a cast refused at its final price");
        helper.assertTrue(!IronsProxyCastDriver.onNativeCooldown(player, "irons_spellbooks:heal"), "no successful-cast cooldown");
        IronsProxyCastDriver.initiateViaSpellSelection(player, IronsProxyCastDriver.nativeBook("irons_spellbooks:heal"), 0);
        IronsProxyCastDriver.tickUntilCastEnds(player);
        helper.assertTrue(player.getHealth() > 10.0f, "the affordable Heal runs");
        helper.assertTrue(castCount(player, school) == casts + 1, "progression is credited once for the accepted cast");
        helper.assertTrue(affinity(player, school) == affinity + 1, "affinity is credited once for the accepted cast");
        helper.succeed();
    }

    private static int castCount(ServerPlayer player, String school) {
        return player.getData(com.otectus.arsnspells.data.AttachmentTypes.PROGRESSION.get()).getCastCount(school);
    }

    private static int affinity(ServerPlayer player, String school) {
        return player.getData(com.otectus.arsnspells.data.AttachmentTypes.AFFINITY.get()).getLevel(school);
    }

    // ---- CAST-01: a persistent effect's whole life is billed once -----------------------------

    @GameTest(template = "platform", batch = MODE_ISS, timeoutTicks = 600)
    public static void ironsLoaded_lifetime_magmaBombIsBilledOnceInIssPrimary(GameTestHelper helper) { lifetimeInMode(helper, "iss_primary", "rv_life_iss"); }

    @GameTest(template = "platform", batch = MODE_ARS, timeoutTicks = 600)
    public static void ironsLoaded_lifetime_magmaBombIsBilledOnceInArsPrimary(GameTestHelper helper) { lifetimeInMode(helper, "ars_primary", "rv_life_ars"); }

    @GameTest(template = "platform", batch = MODE_HYBRID, timeoutTicks = 600)
    public static void ironsLoaded_lifetime_magmaBombIsBilledOnceInHybrid(GameTestHelper helper) { lifetimeInMode(helper, "hybrid", "rv_life_hybrid"); }

    @GameTest(template = "platform", batch = MODE_SEPARATE, timeoutTicks = 600)
    public static void ironsLoaded_lifetime_magmaBombIsBilledOnceInSeparate(GameTestHelper helper) { lifetimeInMode(helper, "separate", "rv_life_separate"); }

    @GameTest(template = "platform", batch = MODE_DISABLED, timeoutTicks = 600)
    public static void ironsLoaded_lifetime_magmaBombIsBilledOnceInDisabled(GameTestHelper helper) { lifetimeInMode(helper, "disabled", "rv_life_disabled"); }

    @AfterBatch(batch = MODE_ISS) public static void afterIss(ServerLevel level) { restoreMode(); }
    @AfterBatch(batch = MODE_ARS) public static void afterArs(ServerLevel level) { restoreMode(); }
    @AfterBatch(batch = MODE_HYBRID) public static void afterHybrid(ServerLevel level) { restoreMode(); }
    @AfterBatch(batch = MODE_SEPARATE) public static void afterSeparate(ServerLevel level) { restoreMode(); }
    @AfterBatch(batch = MODE_DISABLED) public static void afterDisabled(ServerLevel level) { restoreMode(); }

    private static volatile String modeBefore;
    private static volatile Double rateBefore;

    private static void lifetimeInMode(GameTestHelper helper, String mode, String name) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        if (modeBefore == null) {
            modeBefore = AnsConfig.MANA_UNIFICATION_MODE.get();
            rateBefore = AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get();
        }
        TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, mode);
        TestConfig.set(AnsConfig.CONVERSION_RATE_ARS_TO_IRON, 2.0);
        BridgeManager.refreshMode();
        helper.assertTrue(mode.equals(BridgeManager.getCurrentMode().getConfigName()), "could not enter " + mode);
        lifetime(helper, name, MethodSelf.INSTANCE, "magma_bomb", GameType.SURVIVAL, 1, false);
    }

    private static void restoreMode() {
        if (modeBefore == null) return;
        TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, modeBefore);
        if (rateBefore != null) TestConfig.set(AnsConfig.CONVERSION_RATE_ARS_TO_IRON, rateBefore);
        BridgeManager.refreshMode();
        modeBefore = null;
        rateBefore = null;
    }

    @GameTest(template = "platform", timeoutTicks = 600)
    public static void ironsLoaded_lifetime_magmaBombTouchIsBilledOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        lifetime(helper, "rv_life_touch", MethodTouch.INSTANCE, "magma_bomb", GameType.SURVIVAL, 1, false);
    }

    @GameTest(template = "platform", timeoutTicks = 900)
    public static void ironsLoaded_lifetime_twoDeliberateMagmaBombsChargeTwice(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        lifetime(helper, "rv_life_twice", MethodSelf.INSTANCE, "magma_bomb", GameType.SURVIVAL, 2, false);
    }

    @GameTest(template = "platform", timeoutTicks = 600)
    public static void ironsLoaded_lifetime_creativeMagmaBombIsFree(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        lifetime(helper, "rv_life_creative", MethodSelf.INSTANCE, "magma_bomb", GameType.CREATIVE, 1, false);
    }

    @GameTest(template = "platform", timeoutTicks = 600)
    public static void ironsLoaded_lifetime_poisonSplashCloudIsBilledOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        lifetime(helper, "rv_life_splash", MethodTouch.INSTANCE, "poison_splash", GameType.SURVIVAL, 1, false);
    }

    /** The same Magma Bomb with natural regeneration on: after the launch the pools can only rise. */
    @GameTest(template = "platform", timeoutTicks = 600)
    public static void ironsLoaded_lifetime_withRegenerationPoolsOnlyRiseAfterLaunch(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        lifetime(helper, "rv_life_regen", MethodSelf.INSTANCE, "magma_bomb", GameType.SURVIVAL, 1, true);
    }

    @GameTest(template = "platform", timeoutTicks = 600)
    public static void ironsLoaded_lifetime_fireBreathPaysEachPulseAndNothingAfter(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        lifetime(helper, "rv_life_breath", MethodSelf.INSTANCE, "fire_breath", GameType.SURVIVAL, 1, false);
    }

    private record Sample(long tick, double ars, double irons, int projectiles, int areas) {}

    /**
     * Cast {@code casts} times (each after the last one's effects are gone) and sample both raw pools
     * every tick until 40 ticks after the last effect, with natural regeneration off (or, with
     * {@code regen}, on: then the pools may only rise after the last execution).
     */
    private static void lifetime(GameTestHelper helper, String name, AbstractCastMethod form, String spell, GameType mode, int casts,
                                 boolean regen) {
        String spellId = "irons_spellbooks:" + spell;
        ServerPlayer player = caster(helper, name);
        player.setGameMode(mode);
        Pig pig = sturdyPig(helper, new Vec3(3.5, 2.0, 2.5));
        ImportedGlyphGameTests.aim(player, pig.getBoundingBox().getCenter());
        double ironsRegen = regen ? Double.NaN : CastReviewIronsSupport.disableIronsRegen(player);
        Consumer<ManaRegenCalcEvent> noArsRegen = event -> { if (event.getEntity() == player) event.setRegen(0.0); };
        if (!regen) NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ManaRegenCalcEvent.class, noArsRegen);
        AABB box = new AABB(player.blockPosition()).inflate(24);
        List<Sample> samples = new ArrayList<>();
        long[] launches = new long[casts];
        int[] done = {0};
        long[] goneTick = {-1};
        Runnable castOnce = () -> {
            ImportedGlyphGameTests.clearCooldowns(player);
            ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(form, ImportedGlyphGameTests.glyph(spell, 1)));
            launches[done[0]++] = helper.getLevel().getServer().getTickCount();
            goneTick[0] = -1;
        };
        castOnce.run();
        helper.onEachTick(() -> {
            long now = helper.getLevel().getServer().getTickCount();
            int projectiles = CastReviewIronsSupport.projectilesOwnedBy(player, box);
            int areas = CastReviewIronsSupport.areasOwnedBy(player, box);
            samples.add(new Sample(now, raw(player, ResourceUnit.ARS_MANA), raw(player, ResourceUnit.IRONS_MANA), projectiles, areas));
            boolean live = projectiles + areas > 0 || GlyphSessions.continuationsFor(player.getUUID()) > 0;
            if (live) goneTick[0] = -1;
            else if (goneTick[0] < 0) {
                goneTick[0] = now;
                if (done[0] < casts && now > launches[done[0] - 1] + 2) castOnce.run();
            }
        });
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(done[0] == casts && goneTick[0] >= 0
                && helper.getLevel().getServer().getTickCount() >= goneTick[0] + AFTERMATH, "waiting for the effects to end"))
            .thenExecute(() -> {
                try {
                    List<PaymentTimeline.Entry> timeline = PaymentTimeline.entries(player.getUUID());
                    long effects = timeline.stream().filter(e -> e.kind() == PaymentTimeline.Kind.NATIVE_EFFECT && e.label().equals(spellId)).count();
                    long commits = timeline.stream().filter(e -> e.kind() == PaymentTimeline.Kind.COMMIT && e.label().startsWith("glyph:")).count();
                    boolean channel = "fire_breath".equals(spell);
                    if (channel) helper.assertTrue(effects >= 2, "a channel runs several pulses: " + effects + "\n" + timeline);
                    else helper.assertTrue(effects == casts, "one native execution per deliberate cast: " + effects + "\n" + timeline);
                    if (mode == GameType.CREATIVE) helper.assertTrue(commits == 0, "creative pays nothing\n" + timeline);
                    else helper.assertTrue(commits == effects, "each execution paid exactly once: " + commits + " for " + effects + "\n" + timeline);
                    long lastEffect = timeline.stream().filter(e -> e.kind() == PaymentTimeline.Kind.NATIVE_EFFECT)
                        .mapToLong(PaymentTimeline.Entry::tick).max().orElse(launches[casts - 1]);
                    helper.assertTrue(timeline.stream().noneMatch(e -> e.kind() == PaymentTimeline.Kind.DEBIT && e.tick() > lastEffect),
                        "no debit after the last execution\n" + timeline);
                    List<Sample> after = samples.stream().filter(s -> s.tick() > lastEffect).toList();
                    helper.assertTrue(!after.isEmpty(), "the lifetime was sampled");
                    Sample first = after.get(0);
                    for (Sample s : after) {
                        if (regen) helper.assertTrue(s.ars() + 1.0e-6 >= first.ars() && s.irons() + 1.0e-6 >= first.irons(),
                            "with regeneration on, the pools only rise after the last execution: " + first + " -> " + s + "\n" + timeline);
                        else helper.assertTrue(Math.abs(s.ars() - first.ars()) < 1.0e-6 && Math.abs(s.irons() - first.irons()) < 1.0e-6,
                            "with regeneration off neither raw pool moves after the last execution: " + first + " -> " + s + "\n" + timeline);
                    }
                    if ("magma_bomb".equals(spell))
                        helper.assertTrue(samples.stream().anyMatch(s -> s.areas() > 0), "the Fire Field existed");
                    helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "sessions return to baseline");
                } finally {
                    if (!regen) {
                        NeoForge.EVENT_BUS.unregister(noArsRegen);
                        CastReviewIronsSupport.restoreIronsRegen(player, ironsRegen);
                    }
                }
            })
            .thenSucceed();
    }

    // ---- Helpers ------------------------------------------------------------------------------

    static ServerPlayer caster(GameTestHelper helper, String name) {
        ServerPlayer player = ImportedGlyphGameTests.caster(helper, name, GameType.SURVIVAL);
        ImportedGlyphGameTests.fund(player);
        PaymentTimeline.enableForTesting(true);
        PaymentTimeline.clear(player.getUUID());
        GlyphSessions.clearOutcomes(player.getUUID());
        return player;
    }

    static Pig sturdyPig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityType.PIG, at);
        pig.setNoAi(true);
        var maxHealth = pig.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(500.0);
        pig.setHealth(500.0f);
        return pig;
    }

    static boolean outcome(ServerPlayer player, String spell, GlyphOutcome outcome, String detail) {
        return GlyphSessions.outcomes(player.getUUID()).stream().anyMatch(o -> o.spell().toString().equals(spell)
            && o.outcome() == outcome && o.detail().contains(detail));
    }

    private static long nativeEffects(ServerPlayer player, String spell) {
        return PaymentTimeline.entries(player.getUUID()).stream()
            .filter(e -> e.kind() == PaymentTimeline.Kind.NATIVE_EFFECT && e.label().equals(spell)).count();
    }

    private static double raw(ServerPlayer player, ResourceUnit unit) {
        var bridge = BridgeManager.getNativeBridge(unit);
        return bridge == null ? Double.NaN : bridge.transactionMana(player);
    }

    /** Glyph reservations and payments debited, less what was refunded, from the timeline's recorded movements. */
    static void assertNetImportedDebit(GameTestHelper helper, ServerPlayer player, double expected) {
        double net = 0;
        for (PaymentTimeline.Entry entry : PaymentTimeline.entries(player.getUUID())) {
            if (!entry.label().startsWith("glyph:")) continue;
            double moved = moved(entry.detail());
            if (entry.kind() == PaymentTimeline.Kind.DEBIT) net += moved;
            else if (entry.kind() == PaymentTimeline.Kind.REFUND) net -= moved;
        }
        helper.assertTrue(Math.abs(net - expected) < 1.0e-3, "net imported debit " + net + ", expected " + expected + ": "
            + PaymentTimeline.entries(player.getUUID()));
    }

    private static double moved(String detail) {
        int at = detail.indexOf("moved=");
        if (at < 0) return 0;
        double total = 0;
        for (String leg : detail.substring(at + 6).split("\\+")) {
            int colon = leg.indexOf(':');
            if (colon < 0) continue;
            try {
                total += Double.parseDouble(leg.substring(colon + 1).trim());
            } catch (NumberFormatException ignored) {
                // "none"
            }
        }
        return total;
    }
}
