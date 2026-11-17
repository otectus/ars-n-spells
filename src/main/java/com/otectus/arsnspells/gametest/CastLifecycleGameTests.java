package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectInvisibility;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.otectus.arsnspells.casting.PaymentTimeline;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.glyph.GlyphChildren;
import com.otectus.arsnspells.glyph.GlyphSessions;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.function.BiConsumer;

/**
 * 3.4.1 review CAST-02: a charged imported glyph's held recipe tail is cancellable until it resumes,
 * a completed child closes its session at once, and nothing of a dead, departed or disabled cast
 * runs afterwards.
 *
 * <p>Each recipe is Touch on a sturdy pig: a charged Fireball, then Invisibility. Iron's own charge
 * is driven to its release; the child is finished at the end of that tick, the tail is queued, and
 * it resumes at the end of the next one. The tests that invalidate the cast do it in between, while
 * the tail is queued, with the event Forge fires for that change (a test player cannot really die,
 * log out or travel), and then observe that the pig never turns invisible.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class CastLifecycleGameTests {
    private static final String FIREBALL = "irons_spellbooks:fireball";
    private static final String SLOW = "irons_spellbooks:slow";
    private static final String DISABLE_BATCH = "ans_lifecycle_disable";

    private CastLifecycleGameTests() {}

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_charged_lastInRecipeReturnsToBaselineImmediately(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "life_last");
        TouchRegressionGameTests.lookAtSky(player);
        var sequence = helper.startSequence();
        for (int i = 0; i < 3; i++) {
            sequence.thenExecute(() -> {
                IronsGlyphTestSupport.clearCooldowns(player);
                ImportedGlyphGameTests.cast(helper, player, new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(FIREBALL, 1)));
                helper.assertTrue(IronsGlyphTestSupport.casting(player), "the imported Fireball charges in Iron's slot");
                IronsProxyCastDriver.tickUntilCastEnds(player);
            }).thenExecuteAfter(2, () -> {
                helper.assertTrue(baseline(player),
                    "a charged glyph that is the last part closes its session as soon as it completes: " + GlyphSessions.bookkeeping());
                var records = GlyphSessions.records(player.getUUID());
                var last = records.isEmpty() ? null : records.get(records.size() - 1);
                helper.assertTrue(last != null && "child".equals(last.stage()) && last.outcome().startsWith("completed")
                        && last.finalLevel() >= 1 && !"nothing".equals(last.paid()),
                    "/ans glyph last shows the completed child with its paid price and committed level, not only 'accepted_pending': " + last);
            });
        }
        sequence.thenSucceed();
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_charged_tailResumesAfterCompletion(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        runTail(helper, "life_tail_ok", null, true);
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_charged_queuedTailNeverRunsAfterDeath(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        runTail(helper, "life_tail_death", (player, pig) ->
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().fellOutOfWorld())), false);
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_charged_queuedTailNeverRunsAfterLogout(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        runTail(helper, "life_tail_logout", (player, pig) ->
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player)), false);
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_charged_queuedTailNeverRunsAfterDimensionChange(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        runTail(helper, "life_tail_dim", (player, pig) ->
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, Level.NETHER)), false);
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_charged_queuedTailNeverRunsWhenTheTargetIsRemoved(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        runTail(helper, "life_tail_gone", (player, pig) -> pig.discard(), false);
    }

    @GameTest(template = "platform", batch = DISABLE_BATCH, timeoutTicks = 200)
    public static void ironsLoaded_charged_queuedTailNeverRunsWhenGlyphsAreDisabled(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        runTail(helper, "life_tail_off", (player, pig) -> {
            TestConfig.set(AnsConfig.ENABLE_IRON_GLYPHS, false);
            GlyphBehavior.onConfigChanged();
        }, false);
    }

    @AfterBatch(batch = DISABLE_BATCH)
    public static void afterDisable(ServerLevel level) {
        TestConfig.set(AnsConfig.ENABLE_IRON_GLYPHS, true);
        GlyphBehavior.onConfigChanged();
    }

    /** Charged into charged: the resumed tail starts its own child once the slot is free. */
    @GameTest(template = "platform", timeoutTicks = 300)
    public static void ironsLoaded_charged_chargedTailStartsItsOwnChild(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "life_tail_chain");
        Pig pig = sturdyPig(helper);
        TouchRegressionGameTests.aimAt(player, pig.getBoundingBox().getCenter());
        ImportedGlyphGameTests.cast(helper, player, new Spell(MethodTouch.INSTANCE, ImportedGlyphGameTests.glyph(FIREBALL, 1),
            ImportedGlyphGameTests.glyph(SLOW, 1)));
        IronsProxyCastDriver.tickUntilCastEnds(player);
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(IronsGlyphTestSupport.casting(player)
                && SLOW.equals(IronsGlyphTestSupport.castingSpell(player)), "the resumed tail's charged Slow starts in the freed slot"))
            .thenExecute(() -> IronsProxyCastDriver.tickUntilCastEnds(player))
            .thenWaitUntil(() -> helper.assertTrue(IronsGlyphTestSupport.slowed(pig), "the second child's effect lands"))
            .thenExecuteAfter(2, () -> helper.assertTrue(baseline(player), "both children done: " + GlyphSessions.bookkeeping()))
            .thenSucceed();
    }

    /** A native cast of the same spell after an imported child is the player's own, never the child. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_charged_laterNativeCastOfTheSameSpellIsNotTheChild(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "life_native_after");
        TouchRegressionGameTests.lookAtSky(player);
        ImportedGlyphGameTests.cast(helper, player, new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(FIREBALL, 1)));
        IronsProxyCastDriver.tickUntilCastEnds(player);
        helper.startSequence()
            .thenExecuteAfter(2, () -> {
                helper.assertTrue(baseline(player), "the imported child is done: " + GlyphSessions.bookkeeping());
                IronsGlyphTestSupport.clearCooldowns(player);
                helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(player, IronsProxyCastDriver.nativeBook(FIREBALL), 0)
                    && FIREBALL.equals(IronsGlyphTestSupport.castingSpell(player)), "a native Fireball charges");
                helper.assertTrue(GlyphChildren.of(player.getUUID()) == null, "no child record claims the native cast");
                helper.assertTrue(!com.otectus.arsnspells.casting.IronsCastPayments.isCrossCast(player),
                    "the native cast is priced as a native cast, not as the imported child");
            })
            .thenExecuteAfter(3, () -> {
                helper.assertTrue(FIREBALL.equals(IronsGlyphTestSupport.castingSpell(player)), "nothing cancelled the native cast");
                IronsGlyphTestSupport.cancelNativeCast(player);
            })
            .thenSucceed();
    }

    // ---- Harness -------------------------------------------------------------------------------

    /**
     * Cast Touch pig: [charged Fireball, Invisibility], release the Fireball, and between the child
     * finishing and its tail resuming apply {@code invalidate}. {@code resumes}: whether the pig must
     * end up invisible.
     */
    private static void runTail(GameTestHelper helper, String name, BiConsumer<ServerPlayer, Pig> invalidate, boolean resumes) {
        ServerPlayer player = caster(helper, name);
        Pig pig = sturdyPig(helper);
        TouchRegressionGameTests.aimAt(player, pig.getBoundingBox().getCenter());
        ImportedGlyphGameTests.cast(helper, player, new Spell(MethodTouch.INSTANCE, ImportedGlyphGameTests.glyph(FIREBALL, 1),
            EffectInvisibility.INSTANCE));
        helper.assertTrue(IronsGlyphTestSupport.casting(player), "the imported Fireball charges in Iron's slot");
        IronsProxyCastDriver.tickUntilCastEnds(player);
        helper.startSequence()
            .thenExecuteAfter(1, () -> {
                helper.assertTrue(!pig.hasEffect(MobEffects.INVISIBILITY), "the tail has not resumed in the tick the child finished");
                helper.assertTrue(GlyphSessions.queuedFor(player.getUUID()) == 1, "the completed child queued its tail: " + GlyphSessions.bookkeeping());
                if (invalidate != null) invalidate.accept(player, pig);
            })
            .thenExecuteAfter(4, () -> {
                if (resumes) helper.assertTrue(pig.hasEffect(MobEffects.INVISIBILITY), "the held tail resumes after the charge completes");
                else helper.assertTrue(pig.isRemoved() || !pig.hasEffect(MobEffects.INVISIBILITY),
                    "an invalidated cast never runs its queued tail");
                helper.assertTrue(baseline(player), "sessions, children and queued tails return to baseline: " + GlyphSessions.bookkeeping());
            })
            .thenSucceed();
    }

    private static ServerPlayer caster(GameTestHelper helper, String name) {
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, name);
        player.setGameMode(GameType.SURVIVAL);
        TouchRegressionGameTests.fundArs(player);
        IronsGlyphTestSupport.fundIronsPool(player, 5000.0f);
        IronsGlyphTestSupport.clearCooldowns(player);
        return player;
    }

    private static Pig sturdyPig(GameTestHelper helper) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(2.5, 2.0, 2.5));
        pig.setInvulnerable(false);
        var maxHealth = pig.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(500.0);
        pig.setHealth(500.0f);
        return pig;
    }

    /** No open session, child or queued tail of this player's. */
    /**
     * 3.4.1 review CAST-04: a native {@link LinkageError} (an addon built against another Iron's build) is
     * contained like any failure: for an instant imported glyph after its effect began (paid, nothing
     * refunded) and for a charge's release inside Iron's own cast slot, instead of escaping into the tick.
     */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void ironsLoaded_failure_aLinkageErrorAfterTheEffectBeganIsContained(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = CastPolicyGameTests.caster(helper, "life_link_instant");
        net.minecraft.world.entity.animal.Pig pig = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG,
            new net.minecraft.world.phys.Vec3(2.5, 2.0, 3.5));
        TouchRegressionGameTests.aimAt(player, pig.getBoundingBox().getCenter());
        AutoCloseable fault = CastLifetimeIronsSupport.armSpawnLinkage(player);
        try {
            ImportedGlyphGameTests.cast(helper, player, new com.hollingsworth.arsnouveau.api.spell.Spell(
                com.hollingsworth.arsnouveau.common.spell.method.MethodTouch.INSTANCE, ImportedGlyphGameTests.glyph("irons_spellbooks:firebolt", 1)));
        } finally {
            try { fault.close(); } catch (Exception ignored) { }
        }
        var timeline = PaymentTimeline.entries(player.getUUID());
        helper.assertTrue(timeline.stream().anyMatch(e -> e.kind() == PaymentTimeline.Kind.EMISSION && e.detail().contains("FAILED_AFTER_PAYMENT")),
            "the linkage failure after the effect began is contained as FAILED_AFTER_PAYMENT: " + timeline);
        helper.assertTrue(timeline.stream().noneMatch(e -> e.kind() == PaymentTimeline.Kind.REFUND && e.label().startsWith("glyph:")),
            "a begun effect keeps its payment: " + timeline);
        helper.succeedWhen(() -> helper.assertTrue(baseline(player), "the session closes"));
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_failure_aLinkageErrorAtAChargesReleaseIsContained(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = CastPolicyGameTests.caster(helper, "life_link_charge");
        TouchRegressionGameTests.lookAtSky(player);
        AutoCloseable fault = CastLifetimeIronsSupport.armSpawnLinkage(player);
        try {
            ImportedGlyphGameTests.cast(helper, player, new com.hollingsworth.arsnouveau.api.spell.Spell(
                com.hollingsworth.arsnouveau.common.spell.method.MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph("irons_spellbooks:magma_bomb", 1)));
            IronsProxyCastDriver.tickUntilCastEnds(player);
        } finally {
            try { fault.close(); } catch (Exception ignored) { }
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(baseline(player), "the charge's session closes after its release failed");
            helper.assertTrue(PaymentTimeline.entries(player.getUUID()).stream().anyMatch(e -> e.kind() == PaymentTimeline.Kind.NATIVE_ABORT),
                "the release's failure was recorded, not thrown into the tick: " + PaymentTimeline.entries(player.getUUID()));
        });
    }

    static boolean baseline(ServerPlayer player) {
        return GlyphSessions.forPlayer(player.getUUID()).isEmpty() && GlyphChildren.of(player.getUUID()) == null
            && GlyphSessions.queuedFor(player.getUUID()) == 0;
    }
}
