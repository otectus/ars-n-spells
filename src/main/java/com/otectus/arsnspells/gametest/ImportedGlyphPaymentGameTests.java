package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAOE;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.casting.QuoteService;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.runtime.GlyphOutcome;
import com.otectus.arsnspells.glyph.runtime.GlyphPayments;
import com.otectus.arsnspells.glyph.runtime.GlyphSessions;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.otectus.arsnspells.gametest.ImportedGlyphGameTests.aim;
import static com.otectus.arsnspells.gametest.ImportedGlyphGameTests.cast;
import static com.otectus.arsnspells.gametest.ImportedGlyphGameTests.caster;
import static com.otectus.arsnspells.gametest.ImportedGlyphGameTests.fund;
import static com.otectus.arsnspells.gametest.ImportedGlyphGameTests.glyph;
import static com.otectus.arsnspells.gametest.ImportedGlyphGameTests.hasEffect;
import static com.otectus.arsnspells.gametest.ImportedGlyphGameTests.pig;
import static com.otectus.arsnspells.gametest.ImportedGlyphGameTests.spell;

/**
 * Imported-glyph payment against the real pools in every mana mode.
 *
 * <p>Every scenario is synchronous: it changes the shared server config, casts, measures and
 * restores the config within one test tick, so the concurrently scheduled tests of its batch
 * never observe a foreign setting. The spell is a real Ars spellbook cast ({@code Self -> Iron
 * Heal} unless stated), so both legs go through their production boundaries: the Ars leg through
 * Ars's resolver and ANS's Ars payment plan, the imported leg through the glyph session.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class ImportedGlyphPaymentGameTests {
    private static final String[] MODES = {"disabled", "separate", "ars_primary", "iss_primary", "hybrid"};
    private static final ResourceLocation HEAL = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "heal");
    private static final double CROSS = 1.5;

    private ImportedGlyphPaymentGameTests() {
    }

    /**
     * The routing table of the 3.4.0 brief: the native Ars portion and the imported Iron's portion
     * of an ordinary Ars book each follow the mode, the foreign leg carries the cross-cast multiplier
     * exactly once, and non-unit rates convert only across a pool boundary.
     */
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_glyphLegsRouteThroughEveryManaMode(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> {
            for (String mode : MODES) {
                for (double rate : new double[]{0.5, 1, 3}) {
                    settings(mode, rate, "flat_legacy");
                    ServerPlayer player = caster(helper, "glyph_route_" + mode + rate, GameType.SURVIVAL);
                    fund(player);
                    player.setHealth(10.0f);
                    Spell spell = spell(MethodSelf.INSTANCE, glyph("heal", 1));
                    int arsCost = spell.getCost();
                    int ironsCost = IronGlyphBridges.get().manaCost(HEAL, 1);
                    float ars = balance(player, ResourceUnit.ARS_MANA);
                    float irons = balance(player, ResourceUnit.IRONS_MANA);
                    cast(player, spell);
                    String label = mode + " @" + rate;
                    helper.assertTrue(player.getHealth() > 10.0f, "the paid glyph must heal (" + label + ")");
                    equal(helper, ars - owed(mode, ResourceUnit.ARS_MANA, ResourceUnit.ARS_MANA, arsCost, rate, false)
                            - owed(mode, ResourceUnit.IRONS_MANA, ResourceUnit.ARS_MANA, ironsCost, rate, true),
                        balance(player, ResourceUnit.ARS_MANA), "Ars pool " + label);
                    equal(helper, irons - owed(mode, ResourceUnit.ARS_MANA, ResourceUnit.IRONS_MANA, arsCost, rate, false)
                            - owed(mode, ResourceUnit.IRONS_MANA, ResourceUnit.IRONS_MANA, ironsCost, rate, true),
                        balance(player, ResourceUnit.IRONS_MANA), "Iron's pool " + label);
                }
            }
        });
        helper.succeed();
    }

    /**
     * Equal-percentage conversion with asymmetric pool maxima (Ars 10000, Iron's 4000): a converted
     * leg costs the same fraction of the paying pool as the origin price is of its own pool.
     */
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_glyphLegsUseEqualPercentWithAsymmetricMaxima(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> {
            for (String mode : new String[]{"separate", "ars_primary", "iss_primary"}) {
                settings(mode, 1, "equal_percent");
                ServerPlayer player = caster(helper, "glyph_pct_" + mode, GameType.SURVIVAL);
                fund(player);
                IronsProxyCastDriver.setMaxIronsMana(player, 4000.0);
                IronsProxyCastDriver.setIronsMana(player, 4000.0f);
                player.setHealth(10.0f);
                Spell spell = spell(MethodSelf.INSTANCE, glyph("heal", 1));
                int arsCost = spell.getCost();
                int ironsCost = IronGlyphBridges.get().manaCost(HEAL, 1);
                double arsToIrons = 4000.0 / 10000.0;
                double ironsToArs = 10000.0 / 4000.0;
                float ars = balance(player, ResourceUnit.ARS_MANA);
                float irons = balance(player, ResourceUnit.IRONS_MANA);
                cast(player, spell);
                helper.assertTrue(player.getHealth() > 10.0f, "the paid glyph must heal (" + mode + ")");
                double arsDebit;
                double ironsDebit;
                switch (mode) {
                    case "separate" -> {
                        arsDebit = arsCost + Math.round(ironsCost * CROSS * 0.5 * ironsToArs);
                        ironsDebit = Math.round(ironsCost * CROSS * 0.5);
                    }
                    case "ars_primary" -> {
                        arsDebit = arsCost + Math.round(ironsCost * CROSS * ironsToArs);
                        ironsDebit = 0;
                    }
                    default -> {
                        arsDebit = 0;
                        ironsDebit = Math.round(arsCost * arsToIrons) + Math.round(ironsCost * CROSS);
                    }
                }
                equal(helper, ars - arsDebit, balance(player, ResourceUnit.ARS_MANA), "Ars pool equal_percent " + mode);
                equal(helper, irons - ironsDebit, balance(player, ResourceUnit.IRONS_MANA), "Iron's pool equal_percent " + mode);
            }
        });
        helper.succeed();
    }

    /** A shortage in either pool refuses the whole cast before any effect, holding nothing. */
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_glyphShortageRefusesTheWholeCastAtomically(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> {
            settings("disabled", 1, "flat_legacy");
            int heal = (int) Math.round(IronGlyphBridges.get().manaCost(HEAL, 1) * CROSS);
            int oakskin = (int) Math.round(IronGlyphBridges.get().manaCost(
                ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "oakskin"), 1) * CROSS);
            ServerPlayer player = caster(helper, "glyph_short_two", GameType.SURVIVAL);
            fund(player);
            IronsProxyCastDriver.setIronsMana(player, heal + oakskin - 1);
            player.setHealth(10.0f);
            float ars = balance(player, ResourceUnit.ARS_MANA);
            float irons = balance(player, ResourceUnit.IRONS_MANA);
            cast(player, spell(MethodSelf.INSTANCE, glyph("heal", 1), glyph("oakskin", 1)));
            helper.assertTrue(player.getHealth() == 10.0f && !hasEffect(player, "irons_spellbooks:oakskin"),
                "a cast whose second imported glyph cannot be afforded must not run its first");
            equal(helper, ars, balance(player, ResourceUnit.ARS_MANA), "Ars pool after an Iron's shortage");
            equal(helper, irons, balance(player, ResourceUnit.IRONS_MANA), "Iron's pool after an Iron's shortage");

            ServerPlayer broke = caster(helper, "glyph_short_ars", GameType.SURVIVAL);
            fund(broke);
            BridgeManager.getNativeArsBridge().setMana(broke, 0);
            broke.setHealth(10.0f);
            float brokeIrons = balance(broke, ResourceUnit.IRONS_MANA);
            cast(broke, spell(MethodSelf.INSTANCE, glyph("heal", 1)));
            helper.assertTrue(broke.getHealth() == 10.0f, "an unaffordable Ars leg refuses the imported effect too");
            equal(helper, brokeIrons, balance(broke, ResourceUnit.IRONS_MANA), "Iron's pool after an Ars shortage");
        });
        helper.succeed();
    }

    /**
     * Glyphs are learned, reusable abilities: the scroll free-cast setting does not make them free,
     * and a creative caster pays the Iron's leg exactly when Iron's own creative setting says so.
     */
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_glyphIgnoresScrollFreeModeAndFollowsIronsCreativeRule(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        String scrollMode = AnsConfig.SCROLL_COST_MODE.get();
        boolean creativeMana = IronsProxyCastDriver.creativeManaCost();
        withSettings(() -> {
            try {
                settings("iss_primary", 1, "flat_legacy");
                double full = spend(helper, "glyph_scroll_full", GameType.SURVIVAL);
                TestConfig.set(AnsConfig.SCROLL_COST_MODE, "free");
                double free = spend(helper, "glyph_scroll_free", GameType.SURVIVAL);
                equal(helper, full, free, "scroll_cost_mode=free must not change a glyph's price");
                helper.assertTrue(full > 0, "the survival glyph cast must cost something");

                TestConfig.set(AnsConfig.SCROLL_COST_MODE, scrollMode);
                double glyphLeg = glyphQuote(caster(helper, "glyph_quote_probe", GameType.CREATIVE), HEAL, 1);
                IronsProxyCastDriver.setCreativeManaCost(false);
                double exempt = spend(helper, "glyph_creative_off", GameType.CREATIVE);
                IronsProxyCastDriver.setCreativeManaCost(true);
                double charged = spend(helper, "glyph_creative_on", GameType.CREATIVE);
                equal(helper, glyphLeg, charged - exempt,
                    "a creative caster pays the imported leg exactly when Iron's creativeMana setting is on");
            } finally {
                TestConfig.set(AnsConfig.SCROLL_COST_MODE, scrollMode);
                IronsProxyCastDriver.setCreativeManaCost(creativeMana);
            }
        });
        helper.succeed();
    }

    /** Another mod's native Iron's cost hook applies once: the glyph pays the hooked price, not both. */
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_nativeCostHookAppliesToTheGlyphLegOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> {
            settings("iss_primary", 1, "flat_legacy");
            ServerPlayer probe = caster(helper, "glyph_hook_probe", GameType.SURVIVAL);
            fund(probe);
            int cost = IronGlyphBridges.get().manaCost(HEAL, 1);
            double plain = spend(helper, "glyph_hook_plain", GameType.SURVIVAL);
            double arsLeg = plain - glyphQuote(probe, HEAL, 1);
            Runnable unhook = IronsProxyCastDriver.halveManaCostOf(HEAL.toString());
            double hooked;
            try {
                hooked = spend(helper, "glyph_hook_half", GameType.SURVIVAL);
            } finally {
                unhook.run();
            }
            equal(helper, arsLeg + quoteTotal(GlyphPayments.quote(probe, cost / 2, QuoteService.currentRules())), hooked,
                "the glyph must pay the price Iron's own cost event settled on");
        });
        helper.succeed();
    }

    /** The composition, emission and active-session bounds are enforced, and what they refuse is not paid. */
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_glyphBoundsRefuseWithoutCharging(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        int effects = AnsConfig.MAX_IMPORTED_EFFECTS_PER_SPELL.get();
        int emissions = AnsConfig.MAX_FOREIGN_EMISSIONS_PER_CAST.get();
        int sessions = AnsConfig.MAX_ACTIVE_GLYPH_SESSIONS_PER_PLAYER.get();
        withSettings(() -> {
            try {
                settings("iss_primary", 1, "flat_legacy");
                Spell three = spell(MethodSelf.INSTANCE, glyph("heal", 1), glyph("oakskin", 1), glyph("evasion", 1));

                bounds(2, 16, 8);
                ServerPlayer composer = caster(helper, "glyph_bound_comp", GameType.SURVIVAL);
                fund(composer);
                composer.setHealth(10.0f);
                double before = ImportedGlyphGameTests.pools(composer);
                cast(composer, three);
                helper.assertTrue(composer.getHealth() == 10.0f, "three imported effects over a limit of two must not cast");
                equal(helper, before, ImportedGlyphGameTests.pools(composer), "a refused composition costs nothing");

                bounds(4, 2, 8);
                ServerPlayer emitter = caster(helper, "glyph_bound_emit", GameType.SURVIVAL);
                fund(emitter);
                emitter.setHealth(10.0f);
                GlyphSessions.clearOutcomes(emitter.getUUID());
                double start = ImportedGlyphGameTests.pools(emitter);
                cast(emitter, three);
                helper.assertTrue(emitter.getHealth() > 10.0f && hasEffect(emitter, "irons_spellbooks:oakskin"),
                    "the first two emissions run");
                helper.assertTrue(!hasEffect(emitter, "irons_spellbooks:evasion"), "the third emission exceeds the bound of two");
                helper.assertTrue(GlyphSessions.outcomes(emitter.getUUID()).stream().anyMatch(o -> o.outcome() == GlyphOutcome.REJECTED
                    && o.detail().contains("refused.emissions")), "the refusal names the emission bound: " + GlyphSessions.outcomes(emitter.getUUID()));
                double arsLeg = quoteTotal(QuoteService.quote(emitter, ResourceUnit.ARS_MANA, three.getCost(),
                    QuoteService.currentRules(), CarrierPolicy.NATIVE_ONLY));
                equal(helper, start - arsLeg - glyphQuote(emitter, HEAL, 1)
                        - glyphQuote(emitter, ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "oakskin"), 1),
                    ImportedGlyphGameTests.pools(emitter), "only the two emissions that ran are paid; the third's hold is released");

                bounds(4, 16, 1);
                ServerPlayer holder = caster(helper, "glyph_bound_sess", GameType.SURVIVAL);
                fund(holder);
                Pig pig = pig(helper);
                aim(holder, pig.getBoundingBox().getCenter());
                GlyphSessions.clearOutcomes(holder.getUUID());
                cast(holder, spell(MethodTouch.INSTANCE, glyph("slow", 1)));
                double pending = ImportedGlyphGameTests.pools(holder);
                cast(holder, spell(MethodTouch.INSTANCE, glyph("slow", 1)));
                helper.assertTrue(GlyphSessions.outcomes(holder.getUUID()).stream().anyMatch(o -> o.outcome() == GlyphOutcome.REJECTED
                    && o.detail().contains("refused.sessions")), "a second session over a bound of one is refused: "
                    + GlyphSessions.outcomes(holder.getUUID()));
                equal(helper, pending, ImportedGlyphGameTests.pools(holder), "the refused session holds nothing");
                GlyphSessions.closeAllFor(holder.getUUID(), "test cleanup");
            } finally {
                bounds(effects, emissions, sessions);
            }
        });
        helper.succeed();
    }

    /** An augment the glyph does not support is refused at composition, before any cost. */
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_unsupportedAugmentIsRefusedBeforeCost(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = caster(helper, "glyph_bad_augment", GameType.SURVIVAL);
        fund(player);
        player.setHealth(10.0f);
        double before = ImportedGlyphGameTests.pools(player);
        cast(player, spell(MethodSelf.INSTANCE, glyph("heal", 1), AugmentAOE.INSTANCE));
        helper.assertTrue(player.getHealth() == 10.0f, "Heal does not support AOE: the composition must be refused");
        equal(helper, before, ImportedGlyphGameTests.pools(player), "a refused composition costs nothing");
        helper.succeed();
    }

    // ---- helpers ----

    /** What one survival or creative {@code Self -> Iron Heal I} cast takes from both pools together. */
    private static double spend(GameTestHelper helper, String scenario, GameType mode) {
        ServerPlayer player = caster(helper, scenario, mode);
        fund(player);
        player.setHealth(10.0f);
        double before = ImportedGlyphGameTests.pools(player);
        cast(player, spell(MethodSelf.INSTANCE, glyph("heal", 1)));
        helper.assertTrue(player.getHealth() > 10.0f, scenario + ": the glyph must heal");
        return before - ImportedGlyphGameTests.pools(player);
    }

    static double glyphQuote(ServerPlayer player, ResourceLocation spell, int level) {
        return quoteTotal(GlyphPayments.quote(player, IronGlyphBridges.get().manaCost(spell, level), QuoteService.currentRules()));
    }

    static double quoteTotal(CostQuote quote) {
        return quote.total(ResourceUnit.ARS_MANA) + quote.total(ResourceUnit.IRONS_MANA);
    }

    private static void bounds(int effects, int emissions, int sessions) {
        TestConfig.set(AnsConfig.MAX_IMPORTED_EFFECTS_PER_SPELL, effects);
        TestConfig.set(AnsConfig.MAX_FOREIGN_EMISSIONS_PER_CAST, emissions);
        TestConfig.set(AnsConfig.MAX_ACTIVE_GLYPH_SESSIONS_PER_PLAYER, sessions);
        GlyphBehavior.rebuild();
    }

    private static float balance(ServerPlayer player, ResourceUnit unit) {
        return BridgeManager.getNativeBridge(unit).getMana(player);
    }

    private static void equal(GameTestHelper helper, double expected, double actual, String label) {
        helper.assertTrue(Math.abs(expected - actual) < .02, label + ": expected " + expected + ", got " + actual);
    }

    /** {@code base} of {@code origin} owed by pool {@code target}; mirrors the brief's routing table. */
    private static double owed(String mode, ResourceUnit origin, ResourceUnit target, int base, double rate, boolean cross) {
        double cost = base * (cross ? CROSS : 1);
        if (mode.equals("disabled") || (mode.equals("separate") && !cross)) return origin == target ? Math.round(cost) : 0;
        if (mode.equals("separate")) return Math.round(cost * .5 * (origin == target ? 1 : rate));
        ResourceUnit payer = mode.equals("ars_primary") ? ResourceUnit.ARS_MANA : ResourceUnit.IRONS_MANA;
        return target == payer ? Math.round(cost * (origin == payer ? 1 : rate)) : 0;
    }

    private static void settings(String mode, double rate, String policy) {
        TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, mode);
        TestConfig.set(AnsConfig.CONVERSION_RATE_ARS_TO_IRON, rate);
        TestConfig.set(AnsConfig.CONVERSION_RATE_IRON_TO_ARS, rate);
        TestConfig.set(AnsConfig.CONVERSION_POLICY, policy);
        TestConfig.set(AnsConfig.CROSS_CAST_COST_MULTIPLIER, CROSS);
        TestConfig.set(AnsConfig.DUAL_COST_ARS_PERCENTAGE, .5);
        TestConfig.set(AnsConfig.DUAL_COST_ISS_PERCENTAGE, .5);
        BridgeManager.refreshMode();
    }

    private static void withSettings(Runnable body) {
        String mode = AnsConfig.MANA_UNIFICATION_MODE.get();
        String policy = AnsConfig.CONVERSION_POLICY.get();
        double toIron = AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get(), toArs = AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get();
        double cross = AnsConfig.CROSS_CAST_COST_MULTIPLIER.get();
        double arsShare = AnsConfig.DUAL_COST_ARS_PERCENTAGE.get(), ironShare = AnsConfig.DUAL_COST_ISS_PERCENTAGE.get();
        try {
            body.run();
        } finally {
            TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, mode);
            TestConfig.set(AnsConfig.CONVERSION_POLICY, policy);
            TestConfig.set(AnsConfig.CONVERSION_RATE_ARS_TO_IRON, toIron);
            TestConfig.set(AnsConfig.CONVERSION_RATE_IRON_TO_ARS, toArs);
            TestConfig.set(AnsConfig.CROSS_CAST_COST_MULTIPLIER, cross);
            TestConfig.set(AnsConfig.DUAL_COST_ARS_PERCENTAGE, arsShare);
            TestConfig.set(AnsConfig.DUAL_COST_ISS_PERCENTAGE, ironShare);
            BridgeManager.refreshMode();
        }
    }
}
