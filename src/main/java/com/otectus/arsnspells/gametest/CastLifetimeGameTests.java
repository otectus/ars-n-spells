package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.event.ManaRegenCalcEvent;
import com.hollingsworth.arsnouveau.api.spell.AbstractCastMethod;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.casting.PaymentTimeline;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.glyph.GlyphChildren;
import com.otectus.arsnspells.glyph.GlyphSessions;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 3.4.1 review CAST-01: what an imported spell with a persistent native effect costs over that
 * effect's whole life, observed with identities rather than a mana bar.
 *
 * <p>Each test casts through a real Ars spell book, drives Iron's own charge to its release, then
 * samples both raw native pools every tick through the launch, the impact, every damage tick of
 * the lingering area, its removal and 40 ticks after it, alongside the {@link PaymentTimeline}:
 * every ledger debit, refund and commitment, every native invocation and every imported emission,
 * each labelled with its attempt. Natural regeneration is switched off for the baseline (Iron's
 * {@code MANA_REGEN} attribute and Ars's {@code ManaRegenCalcEvent}), so a pool that moves after
 * the launch can only have been debited; one regeneration-enabled run checks that the pools then
 * only ever rise.
 *
 * <p>Each mode runs in its own batch: the mode is global, and the batch hook restores it even
 * when the test fails.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class CastLifetimeGameTests {
    private static final String MAGMA_BOMB = "irons_spellbooks:magma_bomb";
    private static final String POISON_SPLASH = "irons_spellbooks:poison_splash";
    private static final String FIRE_BREATH = "irons_spellbooks:fire_breath";
    /** Ticks observed after the last persistent effect is gone. */
    private static final int AFTERMATH = 40;
    private static final String MODE_ISS = "ans_lifetime_iss_primary";
    private static final String MODE_ARS = "ans_lifetime_ars_primary";
    private static final String MODE_HYBRID = "ans_lifetime_hybrid";
    private static final String MODE_SEPARATE = "ans_lifetime_separate";
    private static final String MODE_DISABLED = "ans_lifetime_disabled";

    private CastLifetimeGameTests() {}

    // ---- Magma Bomb in every mode ---------------------------------------------------------------

    @GameTest(template = "platform", batch = MODE_ISS, timeoutTicks = 600)
    public static void ironsLoaded_lifetime_magmaBombSelfIsBilledOnceInIssPrimary(GameTestHelper helper) {
        magmaBombInMode(helper, "iss_primary", "life_iss");
    }

    @GameTest(template = "platform", batch = MODE_ARS, timeoutTicks = 600)
    public static void ironsLoaded_lifetime_magmaBombSelfIsBilledOnceInArsPrimary(GameTestHelper helper) {
        magmaBombInMode(helper, "ars_primary", "life_ars");
    }

    @GameTest(template = "platform", batch = MODE_HYBRID, timeoutTicks = 600)
    public static void ironsLoaded_lifetime_magmaBombSelfIsBilledOnceInHybrid(GameTestHelper helper) {
        magmaBombInMode(helper, "hybrid", "life_hybrid");
    }

    @GameTest(template = "platform", batch = MODE_SEPARATE, timeoutTicks = 600)
    public static void ironsLoaded_lifetime_magmaBombSelfIsBilledOnceInSeparate(GameTestHelper helper) {
        magmaBombInMode(helper, "separate", "life_separate");
    }

    @GameTest(template = "platform", batch = MODE_DISABLED, timeoutTicks = 600)
    public static void ironsLoaded_lifetime_magmaBombSelfIsBilledOnceInDisabled(GameTestHelper helper) {
        magmaBombInMode(helper, "disabled", "life_disabled");
    }

    @AfterBatch(batch = MODE_ISS) public static void afterIss(ServerLevel level) { restoreMode(); }
    @AfterBatch(batch = MODE_ARS) public static void afterArs(ServerLevel level) { restoreMode(); }
    @AfterBatch(batch = MODE_HYBRID) public static void afterHybrid(ServerLevel level) { restoreMode(); }
    @AfterBatch(batch = MODE_SEPARATE) public static void afterSeparate(ServerLevel level) { restoreMode(); }
    @AfterBatch(batch = MODE_DISABLED) public static void afterDisabled(ServerLevel level) { restoreMode(); }

    private static volatile String modeBeforeBatch;
    private static volatile Double arsToIronBeforeBatch;

    private static void magmaBombInMode(GameTestHelper helper, String mode, String name) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        if (modeBeforeBatch == null) {
            modeBeforeBatch = AnsConfig.MANA_UNIFICATION_MODE.get();
            arsToIronBeforeBatch = AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get();
        }
        TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, mode);
        // Unequal conversion rates, so a leg routed through the wrong pool shows up as a wrong amount.
        TestConfig.set(AnsConfig.CONVERSION_RATE_ARS_TO_IRON, 2.0);
        BridgeManager.refreshMode();
        helper.assertTrue(mode.equals(BridgeManager.getCurrentMode().getConfigName()), "could not enter " + mode);
        run(helper, new Lifetime(name, MethodSelf.INSTANCE, MAGMA_BOMB, 1, false, false, 1));
    }

    private static void restoreMode() {
        if (modeBeforeBatch == null) return;
        TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, modeBeforeBatch);
        if (arsToIronBeforeBatch != null) TestConfig.set(AnsConfig.CONVERSION_RATE_ARS_TO_IRON, arsToIronBeforeBatch);
        BridgeManager.refreshMode();
        modeBeforeBatch = null;
        arsToIronBeforeBatch = null;
    }

    // ---- Forms, a second cast, regeneration, creative -----------------------------------------

    @GameTest(template = "platform", timeoutTicks = 600)
    public static void ironsLoaded_lifetime_magmaBombTouchIsBilledOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        run(helper, new Lifetime("life_touch", MethodTouch.INSTANCE, MAGMA_BOMB, 1, false, false, 1));
    }

    @GameTest(template = "platform", timeoutTicks = 900)
    public static void ironsLoaded_lifetime_twoDeliberateMagmaBombsChargeTwice(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        run(helper, new Lifetime("life_twice", MethodSelf.INSTANCE, MAGMA_BOMB, 1, false, false, 2));
    }

    @GameTest(template = "platform", timeoutTicks = 600)
    public static void ironsLoaded_lifetime_withRegenerationPoolsOnlyRiseAfterLaunch(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        run(helper, new Lifetime("life_regen", MethodSelf.INSTANCE, MAGMA_BOMB, 1, true, false, 1));
    }

    @GameTest(template = "platform", timeoutTicks = 600)
    public static void ironsLoaded_lifetime_creativeMagmaBombIsFreeForItsWholeLife(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        run(helper, new Lifetime("life_creative", MethodSelf.INSTANCE, MAGMA_BOMB, 1, false, true, 1));
    }

    // ---- Another persistent spell, and a true channel ------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 600)
    public static void ironsLoaded_lifetime_poisonSplashCloudIsBilledOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        run(helper, new Lifetime("life_splash", MethodTouch.INSTANCE, POISON_SPLASH, 1, false, false, 1));
    }

    /** A channel pays for every pulse it actually runs, and nothing once it has ended. */
    @GameTest(template = "platform", timeoutTicks = 600)
    public static void ironsLoaded_lifetime_fireBreathPaysEachPulseAndNothingAfter(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        run(helper, new Lifetime("life_breath", MethodSelf.INSTANCE, FIRE_BREATH, 1, false, false, 1));
    }

    // ---- The harness ---------------------------------------------------------------------------

    /**
     * @param casts how many deliberate casts, each after the previous one's effects are gone
     */
    private record Lifetime(String name, AbstractCastMethod form, String spell, int level, boolean regen, boolean creative, int casts) {}

    /** One tick's observation. */
    private record Sample(long tick, double ars, double irons, double routed, int projectiles, int areas) {}

    private static final class Run {
        final List<Sample> samples = new ArrayList<>();
        final List<Long> launches = new ArrayList<>();
        int castsDone;
        long effectsSeenTick = -1, effectsGoneTick = -1;
        boolean anyEffectSeen;
        float pigHealthAtFirstArea = Float.NaN;
    }

    private static void run(GameTestHelper helper, Lifetime spec) {
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, spec.name());
        player.setGameMode(spec.creative() ? GameType.CREATIVE : GameType.SURVIVAL);
        TouchRegressionGameTests.fundArs(player);
        IronsGlyphTestSupport.fundIronsPool(player, 5000.0f);
        IronsGlyphTestSupport.clearCooldowns(player);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(2.5, 2.0, 2.5));
        pig.setInvulnerable(false);
        var maxHealth = pig.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(500.0);
        pig.setHealth(500.0f);
        TouchRegressionGameTests.aimAt(player, pig.getBoundingBox().getCenter());
        double ironsRegen = spec.regen() ? Double.NaN : CastLifetimeIronsSupport.disableIronsRegen(player);
        Consumer<ManaRegenCalcEvent> noArsRegen = event -> { if (event.getEntity() == player) event.setRegen(0.0); };
        if (!spec.regen()) MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, false, ManaRegenCalcEvent.class, noArsRegen);
        PaymentTimeline.enableForTesting(true);
        PaymentTimeline.clear(player.getUUID());
        AABB box = new AABB(player.blockPosition()).inflate(24);
        Run run = new Run();
        Runnable cleanup = () -> {
            if (!spec.regen()) {
                MinecraftForge.EVENT_BUS.unregister(noArsRegen);
                CastLifetimeIronsSupport.restoreIronsRegen(player, ironsRegen);
            }
        };
        castOnce(helper, player, spec, run);
        helper.onEachTick(() -> {
            long now = helper.getLevel().getServer().getTickCount();
            int projectiles = CastLifetimeIronsSupport.projectilesOwnedBy(player, box);
            int areas = CastLifetimeIronsSupport.areasOwnedBy(player, box);
            run.samples.add(new Sample(now, raw(player, ResourceUnit.ARS_MANA), raw(player, ResourceUnit.IRONS_MANA),
                BridgeManager.getBridge().getMana(player), projectiles, areas));
            boolean live = projectiles + areas > 0 || IronsGlyphTestSupport.casting(player);
            if (live) {
                run.anyEffectSeen = true;
                run.effectsGoneTick = -1;
                if (areas > 0 && Float.isNaN(run.pigHealthAtFirstArea)) run.pigHealthAtFirstArea = pig.getHealth();
            } else if (run.anyEffectSeen && run.effectsGoneTick < 0) {
                run.effectsGoneTick = now;
                if (run.castsDone < spec.casts()) {
                    // The next deliberate cast, once the previous one's effects are all gone.
                    IronsGlyphTestSupport.clearCooldowns(player);
                    castOnce(helper, player, spec, run);
                }
            }
        });
        helper.startSequence()
            .thenWaitUntil(() -> {
                long now = helper.getLevel().getServer().getTickCount();
                helper.assertTrue(run.castsDone == spec.casts() && run.anyEffectSeen && run.effectsGoneTick >= 0
                    && now >= run.effectsGoneTick + AFTERMATH, "waiting for the effects to end");
            })
            .thenExecute(() -> {
                try {
                    evaluate(helper, player, spec, run, pig);
                } finally {
                    cleanup.run();
                }
            })
            .thenSucceed();
    }

    private static void castOnce(GameTestHelper helper, ServerPlayer player, Lifetime spec, Run run) {
        Spell spell = new Spell(spec.form(), ImportedGlyphGameTests.glyph(spec.spell(), spec.level()));
        ImportedGlyphGameTests.cast(helper, player, spell);
        // A charged or channelled glyph runs in Iron's own slot; drive it to its end now.
        IronsProxyCastDriver.tickUntilCastEnds(player);
        run.launches.add((long) helper.getLevel().getServer().getTickCount());
        run.castsDone++;
        // The cast itself is the first effect: a channel's cone is gone by the next tick.
        run.anyEffectSeen = true;
        run.effectsGoneTick = -1;
    }

    private static void evaluate(GameTestHelper helper, ServerPlayer player, Lifetime spec, Run run, Pig pig) {
        List<PaymentTimeline.Entry> timeline = PaymentTimeline.entries(player.getUUID());
        String trace = describe(timeline);
        long effects = timeline.stream().filter(e -> e.kind() == PaymentTimeline.Kind.NATIVE_EFFECT && e.label().equals(spec.spell())).count();
        List<PaymentTimeline.Entry> nativePayments = timeline.stream()
            .filter(e -> e.kind() == PaymentTimeline.Kind.COMMIT && e.label().startsWith("native-payment:")).toList();
        boolean channel = FIRE_BREATH.equals(spec.spell());
        if (channel) {
            helper.assertTrue(effects >= 2, "a channel runs several pulses, saw " + effects + "\n" + trace);
        } else {
            helper.assertTrue(effects == spec.casts(), "exactly one native execution per deliberate cast: expected "
                + spec.casts() + ", saw " + effects + "\n" + trace);
        }
        if (spec.creative()) {
            helper.assertTrue(nativePayments.isEmpty() && timeline.stream().noneMatch(e -> e.kind() == PaymentTimeline.Kind.DEBIT
                    && e.label().startsWith("glyph:")),
                "a creative caster Iron's does not charge pays nothing for the imported spell\n" + trace);
        } else {
            helper.assertTrue(nativePayments.size() == effects,
                "every native execution is paid exactly once: " + nativePayments.size() + " payments for " + effects + " executions\n" + trace);
        }
        // Nothing is debited after the last execution's release, through impact, every damage tick,
        // removal and the aftermath: debits after a launch belong only to a later deliberate cast.
        long lastLaunch = run.launches.get(run.launches.size() - 1);
        long lastEffectTick = timeline.stream().filter(e -> e.kind() == PaymentTimeline.Kind.NATIVE_EFFECT)
            .mapToLong(PaymentTimeline.Entry::tick).max().orElse(lastLaunch);
        List<PaymentTimeline.Entry> late = timeline.stream()
            .filter(e -> e.kind() == PaymentTimeline.Kind.DEBIT && e.tick() > Math.max(lastLaunch, lastEffectTick)).toList();
        helper.assertTrue(late.isEmpty(), "no debit after the last execution: " + late + "\n" + trace);
        List<Sample> after = run.samples.stream().filter(s -> s.tick() > Math.max(lastLaunch, lastEffectTick)).toList();
        helper.assertTrue(!after.isEmpty(), "the lifetime was sampled");
        Sample first = after.get(0);
        for (Sample s : after) {
            if (spec.regen()) {
                helper.assertTrue(s.ars() + 1.0e-6 >= first.ars() && s.irons() + 1.0e-6 >= first.irons(),
                    "with regeneration on, the pools only rise after the launch: " + first + " -> " + s + "\n" + trace);
            } else {
                helper.assertTrue(Math.abs(s.ars() - first.ars()) < 1.0e-6 && Math.abs(s.irons() - first.irons()) < 1.0e-6,
                    "with regeneration off, neither raw pool moves after the launch: " + first + " -> " + s + "\n" + trace);
            }
        }
        if (MAGMA_BOMB.equals(spec.spell())) {
            helper.assertTrue(after.stream().anyMatch(s -> s.areas() > 0), "the Fire Field existed\n" + trace);
            helper.assertTrue(!Float.isNaN(run.pigHealthAtFirstArea) && (pig.isRemoved() || pig.getHealth() < run.pigHealthAtFirstArea),
                "the persistent field still damages what stands in it: " + run.pigHealthAtFirstArea + " -> " + pig.getHealth());
        }
        helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty() && GlyphChildren.of(player.getUUID()) == null,
            "sessions and children return to baseline: " + GlyphSessions.bookkeeping());
    }

    private static double raw(ServerPlayer player, ResourceUnit unit) {
        var bridge = BridgeManager.getNativeBridge(unit);
        return bridge == null ? Double.NaN : bridge.transactionMana(player);
    }

    private static String describe(List<PaymentTimeline.Entry> timeline) {
        StringBuilder out = new StringBuilder("timeline:");
        for (PaymentTimeline.Entry entry : timeline) out.append("\n  ").append(entry);
        return out.toString();
    }
}
