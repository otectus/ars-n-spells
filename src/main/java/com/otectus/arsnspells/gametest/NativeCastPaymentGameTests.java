package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.hollingsworth.arsnouveau.api.spell.*;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.spell.*;
import com.otectus.arsnspells.util.ManaUtil;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;
import java.util.function.Consumer;

/** Real native resolvers, real mana capabilities, and observable healing at the payment boundary. */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class NativeCastPaymentGameTests {
    private static final String[] MODES = {"disabled", "separate", "ars_primary", "iss_primary", "hybrid"};

    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void arsNative_successAndLateVetoHaveExactPoolDeltas(GameTestHelper helper) {
        withSettings(() -> {
            for (String mode : IronsCompat.isLoaded() ? MODES : new String[]{"disabled"}) {
                for (double rate : new double[]{0.01, 1, 3}) {
                    settings(mode, rate);
                    for (boolean cross : new boolean[]{false, true}) {
                        ServerPlayer player = player(helper, "ars_payment_" + mode + rate + cross);
                        Spell heal = new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE);
                        var resolver = new SpellResolver(SpellContext.fromEntity(heal, player, new ItemStack(Items.STICK)));
                        if (cross) CrossCastContext.begin(player, CrossSpellType.ARS_NOUVEAU, player.level().getGameTime());
                        try {
                            float ars = balance(player, ResourceUnit.ARS_MANA);
                            float irons = IronsCompat.isLoaded() ? balance(player, ResourceUnit.IRONS_MANA) : 0;
                            int first = resolver.getResolveCost();
                            for (int read = 0; read < 10; read++) helper.assertTrue(first == resolver.getResolveCost(), "fresh cost events must agree");
                            equal(helper, ars, balance(player, ResourceUnit.ARS_MANA), "Ars preview debit");
                            if (IronsCompat.isLoaded()) equal(helper, irons, balance(player, ResourceUnit.IRONS_MANA), "Iron preview debit");
                            helper.assertTrue(resolver.onCast(new ItemStack(Items.STICK), player.level()), "native Ars cast refused: " + mode);
                            helper.assertTrue(player.getHealth() > 10, "successful Ars cast did not heal");
                            equal(helper, ars - owed(mode, ResourceUnit.ARS_MANA, ResourceUnit.ARS_MANA, heal.getCost(), rate, cross),
                                balance(player, ResourceUnit.ARS_MANA), "Ars successful debit " + mode);
                            if (IronsCompat.isLoaded()) equal(helper, irons - owed(mode, ResourceUnit.ARS_MANA, ResourceUnit.IRONS_MANA, heal.getCost(), rate, cross),
                                balance(player, ResourceUnit.IRONS_MANA), "Iron successful debit " + mode);
                        } finally { CrossCastContext.clear(player); }
                    }
                }
            }
            settings(IronsCompat.isLoaded() ? "separate" : "disabled", 3);
            ServerPlayer player = player(helper, "ars_payment_late_cancel");
            Spell heal = new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE);
            SpellResolver resolver = new SpellResolver(SpellContext.fromEntity(heal, player, new ItemStack(Items.STICK)));
            Consumer<SpellCastEvent> veto = event -> { if (event.context == resolver.spellContext) event.setCanceled(true); };
            float ars = balance(player, ResourceUnit.ARS_MANA);
            float irons = IronsCompat.isLoaded() ? balance(player, ResourceUnit.IRONS_MANA) : 0;
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, SpellCastEvent.class, veto);
            CrossCastContext.begin(player, CrossSpellType.ARS_NOUVEAU, player.level().getGameTime());
            try {
                helper.assertTrue(!resolver.onCast(new ItemStack(Items.STICK), player.level()), "late veto must refuse native Ars cast");
                equal(helper, 10, player.getHealth(), "late veto must prevent healing");
                equal(helper, ars, balance(player, ResourceUnit.ARS_MANA), "late veto Ars debit");
                if (IronsCompat.isLoaded()) equal(helper, irons, balance(player, ResourceUnit.IRONS_MANA), "late veto Iron debit");
            } finally { MinecraftForge.EVENT_BUS.unregister(veto); CrossCastContext.clear(player); }
        });
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_nativeCastPaysBeforeHealingAndRefusesBalanceLoss(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> Loaded.irons(helper));
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void publicArsMutationsMoveOnlyTheAuthoritativePool(GameTestHelper helper) {
        withSettings(() -> {
            for (String mode : IronsCompat.isLoaded() ? MODES : new String[]{"disabled"}) {
                settings(mode, 1);
                ServerPlayer player = player(helper, "public_api_" + mode);
                var cap = ManaUtil.getNativeMana(player).orElseThrow(IllegalStateException::new);
                boolean ironsPays = mode.equals("iss_primary") || mode.equals("hybrid");
                ResourceUnit payer = ironsPays ? ResourceUnit.IRONS_MANA : ResourceUnit.ARS_MANA;
                ResourceUnit dormant = ironsPays ? ResourceUnit.ARS_MANA : ResourceUnit.IRONS_MANA;
                float dormantBefore = IronsCompat.isLoaded() ? balance(player, dormant) : 0;
                cap.setMana(500);
                equal(helper, 500, balance(player, payer), "public set must apply once " + mode);
                cap.addMana(125);
                equal(helper, 625, balance(player, payer), "public add must apply once " + mode);
                cap.removeMana(40);
                equal(helper, 585, balance(player, payer), "public remove must apply once " + mode);
                if (IronsCompat.isLoaded()) equal(helper, dormantBefore, balance(player, dormant), "public operations must preserve dormant native balance " + mode);
                if (ironsPays) {
                    com.otectus.arsnspells.bridge.ArsRegenTickScope.enter(player.getUUID());
                    try { cap.addMana(100); }
                    finally { com.otectus.arsnspells.bridge.ArsRegenTickScope.exit(); }
                    equal(helper, 585, balance(player, payer), "only the native regen tick is suppressed");
                    cap.addMana(100);
                    equal(helper, 685, balance(player, payer), "non-regen grant still applies after scope");
                }
                if (IronsCompat.isLoaded()) {
                    BridgeManager.getNativeArsBridge().setMana(player, 123);
                    BridgeManager.getNativeIronsBridge().setMana(player, 456);
                    var saved = cap.serializeNBT();
                    BridgeManager.getNativeArsBridge().setMana(player, 789);
                    cap.deserializeNBT(saved);
                    equal(helper, 123, balance(player, ResourceUnit.ARS_MANA), "save and hydrate must preserve dormant Ars balance");
                    equal(helper, 456, balance(player, ResourceUnit.IRONS_MANA), "native hydration must not overwrite active Iron balance");
                    settings("disabled", 1);
                    equal(helper, 123, balance(player, ResourceUnit.ARS_MANA), "disable preserves independent Ars balance");
                    equal(helper, 456, balance(player, ResourceUnit.IRONS_MANA), "disable preserves independent Iron balance");
                }
            }
        });
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String key) {
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, key);
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(10);
        ManaUtil.getNativeMana(player).orElseThrow(IllegalStateException::new).setMaxMana(10000);
        if (IronsCompat.isLoaded()) Loaded.prepare(player);
        BridgeManager.getNativeArsBridge().setMana(player, 9000);
        return player;
    }
    private static float balance(ServerPlayer player, ResourceUnit unit) { return BridgeManager.getNativeBridge(unit).getMana(player); }
    private static void equal(GameTestHelper helper, double expected, double actual, String label) {
        helper.assertTrue(Math.abs(expected - actual) < .02, label + ": expected " + expected + ", got " + actual);
    }
    private static double owed(String mode, ResourceUnit origin, ResourceUnit target, int base, double rate, boolean cross) {
        double cost = base * (cross ? 1.5 : 1);
        if (mode.equals("disabled") || (mode.equals("separate") && !cross)) return origin == target ? Math.round(cost) : 0;
        if (mode.equals("separate")) return Math.round(cost * .5 * (origin == target ? 1 : rate));
        ResourceUnit payer = mode.equals("ars_primary") ? ResourceUnit.ARS_MANA : ResourceUnit.IRONS_MANA;
        return target == payer ? Math.round(cost * (origin == payer ? 1 : rate)) : 0;
    }
    private static void settings(String mode, double rate) {
        AnsConfig.MANA_UNIFICATION_MODE.set(mode);
        AnsConfig.CONVERSION_RATE_ARS_TO_IRON.set(rate);
        AnsConfig.CONVERSION_RATE_IRON_TO_ARS.set(rate);
        AnsConfig.CROSS_CAST_COST_MULTIPLIER.set(1.5);
        AnsConfig.DUAL_COST_ARS_PERCENTAGE.set(.5);
        AnsConfig.DUAL_COST_ISS_PERCENTAGE.set(.5);
        BridgeManager.refreshMode();
    }
    private static void withSettings(Runnable body) {
        String mode = AnsConfig.MANA_UNIFICATION_MODE.get();
        double toIron = AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get(), toArs = AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get();
        double cross = AnsConfig.CROSS_CAST_COST_MULTIPLIER.get(), arsShare = AnsConfig.DUAL_COST_ARS_PERCENTAGE.get(), ironShare = AnsConfig.DUAL_COST_ISS_PERCENTAGE.get();
        try { body.run(); } finally {
            AnsConfig.MANA_UNIFICATION_MODE.set(mode);
            AnsConfig.CONVERSION_RATE_ARS_TO_IRON.set(toIron);
            AnsConfig.CONVERSION_RATE_IRON_TO_ARS.set(toArs);
            AnsConfig.CROSS_CAST_COST_MULTIPLIER.set(cross);
            AnsConfig.DUAL_COST_ARS_PERCENTAGE.set(arsShare);
            AnsConfig.DUAL_COST_ISS_PERCENTAGE.set(ironShare);
            BridgeManager.refreshMode();
        }
    }
    private static final class Loaded {
        static void prepare(ServerPlayer player) {
            var data = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player);
            data.setServerPlayer(player);
            data.resetCastingState();
            player.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get()).setBaseValue(10000);
            BridgeManager.getNativeIronsBridge().setMana(player, 9000);
        }
        static void irons(GameTestHelper helper) {
            var spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell("irons_spellbooks:heal");
            var source = io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK;
            for (String mode : MODES) for (double rate : new double[]{.01, 1, 3}) for (boolean cross : new boolean[]{false, true}) {
                settings(mode, rate);
                ServerPlayer player = player(helper, "iron_payment_" + mode + rate + cross);
                float ars = balance(player, ResourceUnit.ARS_MANA), irons = balance(player, ResourceUnit.IRONS_MANA);
                if (cross) CrossCastContext.begin(player, CrossSpellType.IRONS_SPELLBOOKS, player.level().getGameTime(), 0, 0, spell.getSpellId());
                try {
                    helper.assertTrue(spell.attemptInitiateCast(ItemStack.EMPTY, 1, player.level(), player, source, false, "mainhand"), "native Iron initiation refused " + mode);
                } finally { CrossCastContext.clear(player); }
                equal(helper, ars, balance(player, ResourceUnit.ARS_MANA), "Iron initiation Ars debit");
                equal(helper, irons, balance(player, ResourceUnit.IRONS_MANA), "Iron initiation Iron debit");
                spell.castSpell(player.level(), 1, player, source, false);
                helper.assertTrue(player.getHealth() > 10, "funded native Iron cast did not heal " + mode);
                equal(helper, ars - owed(mode, ResourceUnit.IRONS_MANA, ResourceUnit.ARS_MANA, spell.getManaCost(1), rate, cross), balance(player, ResourceUnit.ARS_MANA), "Iron cast Ars delta " + mode);
                equal(helper, irons - owed(mode, ResourceUnit.IRONS_MANA, ResourceUnit.IRONS_MANA, spell.getManaCost(1), rate, cross), balance(player, ResourceUnit.IRONS_MANA), "Iron cast Iron delta " + mode);
            }
            settings("separate", 3);
            ServerPlayer player = player(helper, "iron_payment_lost_balance");
            helper.assertTrue(spell.attemptInitiateCast(ItemStack.EMPTY, 1, player.level(), player, source, false, "mainhand"), "funded initiation refused");
            BridgeManager.getNativeIronsBridge().setMana(player, 0);
            float ars = balance(player, ResourceUnit.ARS_MANA);
            spell.castSpell(player.level(), 1, player, source, false);
            equal(helper, 10, player.getHealth(), "lost payment must prevent healing");
            equal(helper, ars, balance(player, ResourceUnit.ARS_MANA), "lost payment must not debit Ars");
            equal(helper, 0, balance(player, ResourceUnit.IRONS_MANA), "lost payment must not go negative");
        }
    }
}
