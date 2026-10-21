package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.util.ManaUtil;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectInvisibility;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.NativeManaAccess;
import com.otectus.arsnspells.casting.QuoteService;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.glyph.EmissionPayment;
import com.otectus.arsnspells.glyph.GlyphSessions;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 3.4.0: what a spell with an imported Iron's glyph costs, observed on the two real pools.
 *
 * <p>The Ars spell's own cost is a native Ars quote; each imported glyph adds Iron's own mana
 * cost of its spell as an Iron's-origin cross quote (the cross-cast multiplier once). Every leg
 * names the pool it is paid from. These tests cast through a real Ars book and check that each
 * pool lost exactly what the quote names for it, once, in every mana mode, with unequal
 * directional rates so a conversion in the wrong direction cannot hide.
 *
 * <p>Own batch: the mana mode and the rates are one global the whole run shares.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class ImportedGlyphPaymentGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportedGlyphPaymentGameTests.class);
    private static final String BATCH = "ans_glyph_modes";
    private static final String HEAL = "irons_spellbooks:heal";

    private ImportedGlyphPaymentGameTests() {}

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 100)
    public static void ironsLoaded_importedSpell_paysEachPoolExactlyItsQuotedLegOnceInEveryMode(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        String previousMode = AnsConfig.MANA_UNIFICATION_MODE.get();
        double previousArsToIron = AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get();
        double previousIronToArs = AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get();
        List<String> report = new ArrayList<>();
        try {
            TestConfig.set(AnsConfig.CONVERSION_RATE_ARS_TO_IRON, 2.0);
            TestConfig.set(AnsConfig.CONVERSION_RATE_IRON_TO_ARS, 0.5);
            for (ManaUnificationMode mode : ManaUnificationMode.values()) {
                TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, mode.getConfigName());
                BridgeManager.refreshMode();
                helper.assertTrue(BridgeManager.getCurrentMode() == mode, "could not enter " + mode + ", got " + BridgeManager.getCurrentMode());
                ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_mode_" + mode.getConfigName());
                player.setGameMode(GameType.SURVIVAL);
                TouchRegressionGameTests.fundArs(player);
                IronsGlyphTestSupport.clearCooldowns(player);
                Spell spell = new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(HEAL, 1));
                CostRules rules = QuoteService.currentRules();
                CostQuote arsLeg = QuoteService.quoteNativeCast(ResourceUnit.ARS_MANA, spell.getCost(), rules);
                CostQuote importedLeg = EmissionPayment.quote(player, rules, IronsGlyphTestSupport.manaCost(HEAL, 1));
                double expectedArs = arsLeg.total(ResourceUnit.ARS_MANA) + importedLeg.total(ResourceUnit.ARS_MANA);
                double expectedIrons = arsLeg.total(ResourceUnit.IRONS_MANA) + importedLeg.total(ResourceUnit.IRONS_MANA);
                double ars = nativeArs(player), irons = IronsProxyCastDriver.ironsMana(player);
                ImportedGlyphGameTests.cast(helper, player, spell);
                double arsSpent = ars - nativeArs(player), ironsSpent = irons - IronsProxyCastDriver.ironsMana(player);
                report.add(mode.getConfigName() + ": ars " + arsSpent + " (quoted " + expectedArs + "), irons " + ironsSpent
                    + " (quoted " + expectedIrons + ")");
                helper.assertTrue(player.getHealth() > 10.0f, mode + ": the imported Heal must resolve");
                helper.assertTrue(Math.abs(arsSpent - expectedArs) < 1.01 && Math.abs(ironsSpent - expectedIrons) < 1.01,
                    mode + ": each pool must lose exactly its quoted leg once; " + report.get(report.size() - 1));
                helper.assertTrue(GlyphSessions.activeFor(player.getUUID()) == 0, mode + ": the glyph session must close");
            }
        } finally {
            TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, previousMode);
            TestConfig.set(AnsConfig.CONVERSION_RATE_ARS_TO_IRON, previousArsToIron);
            TestConfig.set(AnsConfig.CONVERSION_RATE_IRON_TO_ARS, previousIronToArs);
            BridgeManager.refreshMode();
        }
        LOGGER.info("[ANS-GlyphModes] {}", report);
        helper.succeed();
    }

    /** Short of the imported leg: the whole cast is refused and neither pool, the cooldown nor a session changes. */
    @GameTest(template = "platform", batch = BATCH)
    public static void ironsLoaded_shortage_refusesTheWholeCastAtomically(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_shortage");
        player.setGameMode(GameType.SURVIVAL);
        TouchRegressionGameTests.fundArs(player);
        IronsGlyphTestSupport.clearCooldowns(player);
        Spell spell = new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(HEAL, 1));
        CostQuote importedLeg = EmissionPayment.quote(player, QuoteService.currentRules(), IronsGlyphTestSupport.manaCost(HEAL, 1));
        // Starve whichever pool the imported leg is paid from in the current mode.
        if (importedLeg.total(ResourceUnit.IRONS_MANA) > 0) IronsProxyCastDriver.setIronsMana(player, 1.0f);
        else NativeManaAccess.with(player, ResourceUnit.ARS_MANA, () -> {
            ManaUtil.getNativeMana(player).orElseThrow(IllegalStateException::new).setMana(1);
            return null;
        });
        double ars = nativeArs(player), irons = IronsProxyCastDriver.ironsMana(player);
        ImportedGlyphGameTests.cast(helper, player, spell);
        helper.assertTrue(player.getHealth() == 10.0f, "a cast that cannot pay its imported leg must not heal");
        helper.assertTrue(nativeArs(player) == ars && IronsProxyCastDriver.ironsMana(player) == irons,
            "a refused cast must not touch either pool: ars " + ars + " -> " + nativeArs(player) + ", irons " + irons
                + " -> " + IronsProxyCastDriver.ironsMana(player));
        helper.assertTrue(!IronsGlyphTestSupport.onCooldown(player, HEAL), "a refused cast must not start Iron's cooldown");
        helper.assertTrue(GlyphSessions.activeFor(player.getUUID()) == 0, "a refused cast must leave no session");
        helper.succeed();
    }

    /**
     * Cancel a charging imported Fireball: its reserved leg is refunded exactly once, the rest of
     * the recipe never resumes, and only the Ars spell's own leg stays spent.
     */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 100)
    public static void ironsLoaded_cancelledCharge_refundsItsLegOnceAndDropsTheContinuation(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_cancel_ball");
        player.setGameMode(GameType.SURVIVAL);
        TouchRegressionGameTests.fundArs(player);
        // Iron's clamps its pool to the max-mana attribute; a charged Fireball needs more than the default 100.
        IronsGlyphTestSupport.fundIronsPool(player, 1000.0f);
        IronsGlyphTestSupport.clearCooldowns(player);
        Pig pig = TouchRegressionGameTests.pigInFront(helper);
        TouchRegressionGameTests.aimAt(player, pig.getBoundingBox().getCenter());
        Spell spell = new Spell(MethodTouch.INSTANCE, ImportedGlyphGameTests.glyph("irons_spellbooks:fireball", 1), EffectInvisibility.INSTANCE);
        CostQuote arsLeg = QuoteService.quoteNativeCast(ResourceUnit.ARS_MANA, spell.getCost(), QuoteService.currentRules());
        double before = nativeArs(player) + IronsProxyCastDriver.ironsMana(player);
        ImportedGlyphGameTests.cast(helper, player, spell);
        helper.assertTrue(IronsGlyphTestSupport.casting(player), "the imported Fireball must be charging");
        IronsGlyphTestSupport.cancelNativeCast(player);
        helper.startSequence()
            .thenExecuteAfter(5, () -> {
                double spent = before - nativeArs(player) - IronsProxyCastDriver.ironsMana(player);
                double arsOnly = arsLeg.total(ResourceUnit.ARS_MANA) + arsLeg.total(ResourceUnit.IRONS_MANA);
                helper.assertTrue(Math.abs(spent - arsOnly) < 1.01,
                    "only the spell's own leg (" + arsOnly + ") stays spent after cancelling the charge, spent " + spent);
                helper.assertTrue(!pig.hasEffect(MobEffects.INVISIBILITY), "a cancelled charge must not resume the recipe");
                helper.assertTrue(GlyphSessions.activeFor(player.getUUID()) == 0, "the session closes with the cancelled child");
            })
            .thenSucceed();
    }

    static double nativeArs(ServerPlayer player) {
        return NativeManaAccess.with(player, ResourceUnit.ARS_MANA,
            () -> ManaUtil.getNativeMana(player).orElseThrow(IllegalStateException::new).getCurrentMana());
    }
}
