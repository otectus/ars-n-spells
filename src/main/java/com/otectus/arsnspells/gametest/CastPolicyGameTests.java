package com.otectus.arsnspells.gametest;

import com.google.gson.JsonParser;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectDelay;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.otectus.arsnspells.casting.PaymentTimeline;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 3.4.1 review CAST-03: work admitted under one policy and run later is authorized again, against
 * the policy then in force, before it starts. A delayed effect, a charged release and a held tail
 * that the policy no longer allows refuse, and only what they still held is returned; an effect that
 * already ran keeps its payment. Each test that changes a global (Iron's maximum, the mappings, the
 * feature switch, the world border) owns its batch and restores it there.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class CastPolicyGameTests {
    private static final String HEAL = "irons_spellbooks:heal";
    private static final String FIREBALL = "irons_spellbooks:fireball";
    private static final String FIREBOLT = "irons_spellbooks:firebolt";
    private static final String MAX_DELAY_BATCH = "ans_policy_max_delay";
    private static final String MAX_CHARGE_BATCH = "ans_policy_max_charge";
    private static final String DISABLE_BATCH = "ans_policy_disable_charge";
    private static final String MAPPING_BATCH = "ans_policy_mapping";
    private static final String BORDER_BATCH = "ans_policy_border";

    private CastPolicyGameTests() {}

    /** Admitted at level 5 (base 1 and four Amplify), the maximum drops to 2 before the Delay expires. */
    @GameTest(template = "platform", batch = MAX_DELAY_BATCH, timeoutTicks = 200)
    public static void ironsLoaded_policy_delayedEffectAboveALoweredMaximumRefusesAndRefunds(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "policy_max_delay");
        float before = player.getHealth();
        ImportedGlyphGameTests.cast(helper, player, spell(MethodSelf.INSTANCE, true, HEAL, 4));
        helper.assertTrue(!com.otectus.arsnspells.glyph.GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "admitted at level 5");
        maxBefore = Glyph341IronsSupport.setMaxLevel(HEAL, 2);
        GlyphBehavior.rebuild();
        helper.startSequence()
            .thenExecuteAfter(60, () -> {
                helper.assertTrue(player.getHealth() == before, "the delayed Heal at level 5 must not run under a maximum of 2");
                assertNetImportedDebit(helper, player, 0.0);
                helper.assertTrue(CastLifecycleGameTests.baseline(player), "the session closes: "
                    + com.otectus.arsnspells.glyph.GlyphSessions.bookkeeping());
            })
            .thenSucceed();
    }

    /** Admitted at level 3, the maximum drops to 2 while the Fireball charges: its release refuses. */
    @GameTest(template = "platform", batch = MAX_CHARGE_BATCH, timeoutTicks = 200)
    public static void ironsLoaded_policy_chargedReleaseAboveALoweredMaximumRefuses(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "policy_max_charge");
        TouchRegressionGameTests.lookAtSky(player);
        ImportedGlyphGameTests.cast(helper, player, spell(MethodSelf.INSTANCE, false, FIREBALL, 2));
        helper.assertTrue(IronsGlyphTestSupport.casting(player), "the level 3 Fireball charges");
        maxBefore = Glyph341IronsSupport.setMaxLevel(FIREBALL, 2);
        GlyphBehavior.rebuild();
        IronsProxyCastDriver.tickUntilCastEnds(player);
        AABB around = new AABB(player.blockPosition()).inflate(32);
        helper.startSequence()
            .thenExecuteAfter(3, () -> {
                helper.assertTrue(CastLifetimeIronsSupport.projectilesOwnedBy(player, around) == 0, "no Fireball is released");
                helper.assertTrue(timeline(player).stream().noneMatch(e -> e.kind() == PaymentTimeline.Kind.NATIVE_EFFECT),
                    "the release never reached its effect: " + timeline(player));
                assertNetImportedDebit(helper, player, 0.0);
                helper.assertTrue(CastLifecycleGameTests.baseline(player), "the child and session end");
            })
            .thenSucceed();
    }

    @GameTest(template = "platform", batch = DISABLE_BATCH, timeoutTicks = 200)
    public static void ironsLoaded_policy_disablingGlyphsStopsACharge(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "policy_disable");
        TouchRegressionGameTests.lookAtSky(player);
        ImportedGlyphGameTests.cast(helper, player, spell(MethodSelf.INSTANCE, false, FIREBALL, 0));
        helper.assertTrue(IronsGlyphTestSupport.casting(player), "the Fireball charges");
        TestConfig.set(AnsConfig.ENABLE_IRON_GLYPHS, false);
        GlyphBehavior.onConfigChanged();
        AABB around = new AABB(player.blockPosition()).inflate(32);
        helper.startSequence()
            .thenExecuteAfter(2, () -> {
                helper.assertTrue(!IronsGlyphTestSupport.casting(player), "the child is stopped, not left charging");
                IronsProxyCastDriver.tickUntilCastEnds(player);
                helper.assertTrue(CastLifetimeIronsSupport.projectilesOwnedBy(player, around) == 0, "no Fireball is released");
                assertNetImportedDebit(helper, player, 0.0);
                helper.assertTrue(CastLifecycleGameTests.baseline(player), "nothing of the cast remains");
            })
            .thenSucceed();
    }

    /** The target dies while the Delay runs: the delayed Firebolt refuses and returns its hold. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_policy_deadTargetRefusesTheDelayedEffect(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "policy_dead");
        Pig pig = pig(helper);
        TouchRegressionGameTests.aimAt(player, pig.getBoundingBox().getCenter());
        ImportedGlyphGameTests.cast(helper, player, spell(MethodTouch.INSTANCE, true, FIREBOLT, 0));
        pig.kill();
        AABB around = new AABB(player.blockPosition()).inflate(32);
        helper.startSequence()
            .thenExecuteAfter(60, () -> {
                helper.assertTrue(CastLifetimeIronsSupport.projectilesOwnedBy(player, around) == 0, "nothing is cast at a dead target");
                assertNetImportedDebit(helper, player, 0.0);
                helper.assertTrue(CastLifecycleGameTests.baseline(player), "the session closes");
            })
            .thenSucceed();
    }

    /** A Self-only rule, and then a deny rule, published while the Delay runs: each refuses the delayed Touch effect. */
    @GameTest(template = "platform", batch = MAPPING_BATCH, timeoutTicks = 300)
    public static void ironsLoaded_policy_newSelfOnlyAndDenyRulesRefuseDelayedEffects(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "policy_rules");
        Pig pig = pig(helper);
        TouchRegressionGameTests.aimAt(player, pig.getBoundingBox().getCenter());
        float health = pig.getHealth();
        ImportedGlyphGameTests.cast(helper, player, spell(MethodTouch.INSTANCE, true, FIREBOLT, 0));
        publish("{\"schema_version\":2,\"spell\":\"" + FIREBOLT + "\",\"adapter\":\"ars_n_spells:self\"}");
        helper.startSequence()
            .thenExecuteAfter(60, () -> {
                helper.assertTrue(noNativeFirebolt(player), "no native Firebolt ran under a Self-only rule: " + timeline(player));
                helper.assertTrue(pig.getHealth() == health, "a Touch Firebolt refuses under a Self-only rule published mid-flight (pig "
                    + pig.getHealth() + "/" + health + ", last hurt by " + hurtBy(pig) + ")");
                assertNetImportedDebit(helper, player, 0.0);
                GlyphMappingSnapshot.publish(mappingsBefore);
                IronsGlyphTestSupport.clearCooldowns(player);
                PaymentTimeline.clear(player.getUUID());
                ImportedGlyphGameTests.cast(helper, player, spell(MethodTouch.INSTANCE, true, FIREBOLT, 0));
                publish("{\"schema_version\":2,\"spell\":\"" + FIREBOLT + "\",\"enabled\":false}");
            })
            .thenExecuteAfter(60, () -> {
                helper.assertTrue(noNativeFirebolt(player), "no native Firebolt ran under a deny rule: " + timeline(player));
                helper.assertTrue(pig.getHealth() == health, "a Firebolt disabled by a rule published mid-flight does not run (pig "
                    + pig.getHealth() + "/" + health + ", last hurt by " + hurtBy(pig) + ")");
                assertNetImportedDebit(helper, player, 0.0);
                helper.assertTrue(CastLifecycleGameTests.baseline(player), "the sessions close");
            })
            .thenSucceed();
    }

    /** The world border moves away from the target while the Delay runs. */
    @GameTest(template = "platform", batch = BORDER_BATCH, timeoutTicks = 200)
    public static void ironsLoaded_policy_targetOutsideAMovedBorderRefuses(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "policy_border");
        Pig pig = pig(helper);
        TouchRegressionGameTests.aimAt(player, pig.getBoundingBox().getCenter());
        float health = pig.getHealth();
        ImportedGlyphGameTests.cast(helper, player, spell(MethodTouch.INSTANCE, true, FIREBOLT, 0));
        var border = helper.getLevel().getWorldBorder();
        borderBefore = new double[]{border.getCenterX(), border.getCenterZ(), border.getSize()};
        border.setCenter(pig.getX() + 5000, pig.getZ() + 5000);
        border.setSize(16);
        helper.startSequence()
            .thenExecuteAfter(60, () -> {
                restoreBorder(helper.getLevel());
                helper.assertTrue(pig.getHealth() == health, "nothing is cast at a target outside the world border");
                assertNetImportedDebit(helper, player, 0.0);
                helper.assertTrue(CastLifecycleGameTests.baseline(player), "the session closes");
            })
            .thenSucceed();
    }

    /** A native level bonus may still take a legal Ars level above the scroll maximum, through a Delay. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_policy_nativeBonusAboveTheMaximumStillRunsWhenTheArsLevelIsLegal(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "policy_bonus");
        int max = IronsGlyphTestSupport.maxLevel(HEAL);
        AutoCloseable probe = Glyph341IronsSupport.probe(player, 2, 1.0);
        float before = player.getHealth();
        ImportedGlyphGameTests.cast(helper, player, spell(MethodSelf.INSTANCE, true, HEAL, max - 1));
        helper.startSequence()
            .thenExecuteAfter(60, () -> {
                try {
                    helper.assertTrue(player.getHealth() > before, "the delayed Heal at the maximum runs");
                    helper.assertTrue(Glyph341IronsSupport.lastCastLevel(probe) == max + 2,
                        "with its native bonus, at level " + (max + 2) + ", got " + Glyph341IronsSupport.lastCastLevel(probe));
                } finally {
                    Glyph341IronsSupport.close(probe);
                }
            })
            .thenSucceed();
    }

    // ---- PROGRESSION-01: training only for an accepted native effect --------------------------

    /**
     * A cost listener makes the native Heal's final price unaffordable after its cast event: no heal,
     * no progression, no affinity, no cooldown. The same cast at its normal price then credits each once.
     */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void ironsLoaded_training_unaffordableFinalPriceCreditsNoTraining(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "training_refused");
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(10.0f);
        IronsGlyphTestSupport.clearCooldowns(player);
        IronsProxyCastDriver.setIronsMana(player, 100.0f);
        String school = "irons_spellbooks:holy";
        int casts = castCount(player, school), affinity = affinity(player, school);
        AutoCloseable probe = Glyph341IronsSupport.probe(player, 0, 1000.0);
        try {
            IronsProxyCastDriver.initiateViaSpellSelection(player, IronsProxyCastDriver.nativeBook(HEAL), 0);
            IronsProxyCastDriver.tickUntilCastEnds(player);
        } finally {
            Glyph341IronsSupport.close(probe);
        }
        helper.assertTrue(player.getHealth() == 10.0f, "the unaffordable Heal has no effect");
        helper.assertTrue(castCount(player, school) == casts, "no progression for a cast refused at its final price");
        helper.assertTrue(affinity(player, school) == affinity, "no affinity for a cast refused at its final price");
        helper.assertTrue(!IronsGlyphTestSupport.onCooldown(player, HEAL), "no successful-cast cooldown");
        IronsProxyCastDriver.initiateViaSpellSelection(player, IronsProxyCastDriver.nativeBook(HEAL), 0);
        IronsProxyCastDriver.tickUntilCastEnds(player);
        helper.assertTrue(player.getHealth() > 10.0f, "the affordable Heal runs");
        helper.assertTrue(castCount(player, school) == casts + 1, "progression is credited once for the accepted cast");
        helper.assertTrue(affinity(player, school) == affinity + 1, "affinity is credited once for the accepted cast");
        helper.succeed();
    }

    private static int castCount(ServerPlayer player, String school) {
        return player.getCapability(com.otectus.arsnspells.data.ProgressionData.PROGRESSION_DATA).map(d -> d.getCastCount(school)).orElse(-1);
    }

    private static int affinity(ServerPlayer player, String school) {
        return player.getCapability(com.otectus.arsnspells.data.AffinityData.AFFINITY_DATA).map(d -> d.getLevel(school)).orElse(-1);
    }

    // ---- Batch restoration ---------------------------------------------------------------------

    private static volatile int maxBefore = -1;
    private static volatile GlyphMappingSnapshot mappingsBefore;
    private static volatile double[] borderBefore;

    @AfterBatch(batch = MAX_DELAY_BATCH)
    public static void afterMaxDelay(ServerLevel level) { restoreMax(HEAL); }

    @AfterBatch(batch = MAX_CHARGE_BATCH)
    public static void afterMaxCharge(ServerLevel level) { restoreMax(FIREBALL); }

    @AfterBatch(batch = DISABLE_BATCH)
    public static void afterDisable(ServerLevel level) {
        TestConfig.set(AnsConfig.ENABLE_IRON_GLYPHS, true);
        GlyphBehavior.onConfigChanged();
    }

    @BeforeBatch(batch = MAPPING_BATCH)
    public static void beforeMapping(ServerLevel level) { mappingsBefore = GlyphMappingSnapshot.current(); }

    @AfterBatch(batch = MAPPING_BATCH)
    public static void afterMapping(ServerLevel level) {
        if (mappingsBefore != null) GlyphMappingSnapshot.publish(mappingsBefore);
    }

    @AfterBatch(batch = BORDER_BATCH)
    public static void afterBorder(ServerLevel level) { restoreBorder(level); }

    private static void restoreMax(String spell) {
        if (maxBefore > 0 && IronsCompat.isLoaded()) {
            Glyph341IronsSupport.setMaxLevel(spell, maxBefore);
            GlyphBehavior.rebuild();
        }
        maxBefore = -1;
    }

    private static void restoreBorder(ServerLevel level) {
        double[] saved = borderBefore;
        if (saved == null) return;
        level.getWorldBorder().setCenter(saved[0], saved[1]);
        level.getWorldBorder().setSize(saved[2]);
        borderBefore = null;
    }

    // ---- Helpers -------------------------------------------------------------------------------

    static ServerPlayer caster(GameTestHelper helper, String name) {
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, name);
        player.setGameMode(GameType.SURVIVAL);
        TouchRegressionGameTests.fundArs(player);
        IronsGlyphTestSupport.fundIronsPool(player, 5000.0f);
        IronsGlyphTestSupport.clearCooldowns(player);
        PaymentTimeline.enableForTesting(true);
        PaymentTimeline.clear(player.getUUID());
        return player;
    }

    private static Pig pig(GameTestHelper helper) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(2.5, 2.0, 2.5));
        pig.setInvulnerable(false);
        var maxHealth = pig.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(200.0);
        pig.setHealth(200.0f);
        return pig;
    }

    /** {@code [form, (Delay,) glyph, Amplify x amplify]}. */
    private static Spell spell(com.hollingsworth.arsnouveau.api.spell.AbstractCastMethod form, boolean delayed, String glyph, int amplify) {
        List<AbstractSpellPart> parts = new ArrayList<>();
        parts.add(form);
        if (delayed) parts.add(EffectDelay.INSTANCE);
        parts.add(ImportedGlyphGameTests.glyph(glyph, 1));
        for (int i = 0; i < amplify; i++) parts.add(AugmentAmplify.INSTANCE);
        return new Spell(parts);
    }

    /** No Firebolt execution reached its effect: no native cast of it, and no imported emission of it that applied. */
    private static boolean noNativeFirebolt(ServerPlayer player) {
        return timeline(player).stream().noneMatch(e -> (e.kind() == PaymentTimeline.Kind.NATIVE_EFFECT && e.label().equals(FIREBOLT))
            || (e.kind() == PaymentTimeline.Kind.EMISSION && e.detail().contains("firebolt")
                && (e.detail().contains("EFFECT_APPLIED") || e.detail().contains("FAILED_AFTER_PAYMENT"))));
    }

    /** What last hurt {@code pig}, for a failure message: a stray hazard is told apart from the glyph. */
    private static String hurtBy(Pig pig) {
        var source = pig.getLastDamageSource();
        return source == null ? "nothing" : source.getMsgId() + (source.getEntity() == null ? "" : " from " + source.getEntity());
    }

    static List<PaymentTimeline.Entry> timeline(ServerPlayer player) {
        return PaymentTimeline.entries(player.getUUID());
    }

    /**
     * What the imported leg cost in the end: every glyph reservation and native payment debited,
     * less what was refunded. Read from the timeline's recorded movements, by attempt label.
     */
    static void assertNetImportedDebit(GameTestHelper helper, ServerPlayer player, double expected) {
        double net = 0;
        for (PaymentTimeline.Entry entry : timeline(player)) {
            if (!entry.label().startsWith("glyph:") && !entry.label().startsWith("native-payment:")) continue;
            double moved = moved(entry.detail());
            if (entry.kind() == PaymentTimeline.Kind.DEBIT) net += moved;
            else if (entry.kind() == PaymentTimeline.Kind.REFUND) net -= moved;
        }
        helper.assertTrue(Math.abs(net - expected) < 1.0e-6, "net imported debit " + net + ", expected " + expected + ": " + timeline(player));
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

    private static void publish(String json) {
        GlyphMappingSnapshot candidate = GlyphMappingSnapshot.build(List.of(new GlyphMappingSnapshot.Source(
            new ResourceLocation("ans_gametest", "policy_rules"), "gametest", 1000, JsonParser.parseString(json))),
            ImportedGlyphCatalog.get(), GlyphMappingSnapshot.nextGeneration());
        if (!candidate.errors().isEmpty()) throw new IllegalStateException("test behaviour file invalid: " + candidate.errors());
        GlyphMappingSnapshot.publish(candidate);
    }
}
