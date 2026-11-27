package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.IronsCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Audit ANS-004: an Ars Mana Regen potion's bonus reaches the paying pool exactly once in every mode.
 *
 * <p>In ISS_PRIMARY and HYBRID Iron's owns the shared pool and Ars's own regen tick is suppressed,
 * so the potion's regeneration must be carried to Iron's regen; HYBRID used to drop it. Capacity is
 * carried only in ISS_PRIMARY: HYBRID's ceiling already holds Ars's real maximum. In ARS_PRIMARY,
 * SEPARATE and DISABLED Ars regenerates its own pool natively and nothing is carried.
 *
 * <p>Each mode measures, in survival with an uncapped pool and no other bonus, Iron's real
 * regeneration over 20 ticks of its native cadence (two {@code regenPlayerMana} calls) for a player
 * with the potion and a control without it. Iron's-only; one batch, since modes are global.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class PotionRegenGameTests {
    private PotionRegenGameTests() {}

    @GameTest(template = "platform", batch = "ans_potion_regen")
    public static void ironsLoaded_potion_regenReachesThePayingPoolOnceInEveryMode(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.everyMode(helper);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_potion_regen_switch")
    public static void ironsLoaded_potion_modeSwitchAndExpiryLeaveNoStaleModifier(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.switchAndExpire(helper);
        helper.succeed();
    }

    private static final class Loaded {
        private static final java.util.UUID POOL = java.util.UUID.fromString("0a000000-0000-0000-0000-00000000a004");
        private static final double RATE = 2.0;
        private static final String[] MODES = {"hybrid", "iss_primary", "ars_primary", "separate", "disabled"};

        private static net.minecraft.world.effect.MobEffect regenPotion() {
            return net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getOptional(
                new net.minecraft.resources.ResourceLocation("ars_nouveau", "mana_regen")).orElseThrow();
        }

        private static net.minecraft.server.level.ServerPlayer player(GameTestHelper helper, String name) {
            var player = CrossCastGameTests.scenarioPlayer(helper, "potion_" + name);
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.removeAllEffects();
            com.otectus.arsnspells.bridge.AnsFeatureCleanup.removeAll(player);
            com.otectus.arsnspells.util.ManaUtil.getNativeMana(player).ifPresent(cap -> {
                cap.setGlyphBonus(0);
                cap.setBookTier(0);
            });
            var max = player.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
            max.removeModifier(POOL);
            max.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(POOL, "ANS potion test pool", 20000,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
            io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player).setServerPlayer(player);
            return player;
        }

        private static double twentyTicksOfIronsRegen(net.minecraft.server.level.ServerPlayer player) {
            var data = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player);
            data.setMana(0.0f);
            var manager = (io.redspace.ironsspellbooks.capabilities.magic.MagicManager) io.redspace.ironsspellbooks.api.magic.MagicHelper.MAGIC_MANAGER;
            // Iron's regenerates every MANA_REGEN_TICKS (10) ticks: two calls in 20 ticks.
            for (int call = 0; call < 20 / io.redspace.ironsspellbooks.capabilities.magic.MagicManager.MANA_REGEN_TICKS; call++)
                manager.regenPlayerMana(player, data);
            return data.getMana();
        }

        private static void inMode(GameTestHelper helper, String mode, Runnable body) {
            String previousMode = com.otectus.arsnspells.config.AnsConfig.MANA_UNIFICATION_MODE.get();
            double previousRate = com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get();
            try {
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.MANA_UNIFICATION_MODE, mode);
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_ARS_TO_IRON, RATE);
                com.otectus.arsnspells.bridge.BridgeManager.refreshMode();
                helper.assertTrue(mode.equals(com.otectus.arsnspells.bridge.BridgeManager.getCurrentMode().getConfigName()),
                    "could not enter " + mode);
                body.run();
            } finally {
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.MANA_UNIFICATION_MODE, previousMode);
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_ARS_TO_IRON, previousRate);
                com.otectus.arsnspells.bridge.BridgeManager.refreshMode();
            }
        }

        static void everyMode(GameTestHelper helper) {
            var potion = regenPotion();
            var withPotion = player(helper, "with");
            var control = player(helper, "control");
            var maxId = com.otectus.arsnspells.bridge.AnsModifierIdentities.uuid(com.otectus.arsnspells.contract.AnsModifierIds.ARS_POTION_MAX_MANA);
            try {
                for (String mode : MODES) {
                    inMode(helper, mode, () -> {
                        withPotion.removeAllEffects();
                        withPotion.addEffect(new net.minecraft.world.effect.MobEffectInstance(potion, 400, 0));
                        com.otectus.arsnspells.equipment.PotionContributions.reconcile(withPotion);
                        com.otectus.arsnspells.equipment.PotionContributions.reconcile(withPotion);
                        com.otectus.arsnspells.equipment.PotionContributions.reconcile(control);
                        double bonus = twentyTicksOfIronsRegen(withPotion) - twentyTicksOfIronsRegen(control);
                        boolean ironsOwned = mode.equals("hybrid") || mode.equals("iss_primary");
                        // Level I: 0.5 nominal Ars regen, times the pool rate, at Ars's real cadence, for one second.
                        double expected = ironsOwned ? 0.5 * RATE * com.otectus.arsnspells.bridge.ManaRegenBridge.liveArsRealPerNominal() : 0.0;
                        System.out.println("ANS_POTION_REGEN mode=" + mode + " ironsBonusDelta20t=" + bonus + " expected=" + expected);
                        helper.assertTrue(Math.abs(bonus - expected) <= 0.01 * Math.max(1.0, expected),
                            mode + ": the potion must add its configured regen to the paying pool exactly once: expected "
                                + expected + " Iron's mana over 20 ticks, measured " + bonus);
                        var max = withPotion.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
                        helper.assertTrue(mode.equals("iss_primary") || max.getModifier(maxId) == null,
                            mode + ": no potion max-mana modifier outside ISS_PRIMARY (HYBRID's ceiling already holds Ars's maximum)");
                    });
                }
            } finally {
                withPotion.removeAllEffects();
                com.otectus.arsnspells.equipment.PotionContributions.reconcile(withPotion);
                com.otectus.arsnspells.bridge.AnsFeatureCleanup.removeAll(withPotion);
                com.otectus.arsnspells.bridge.AnsFeatureCleanup.removeAll(control);
            }
        }

        static void switchAndExpire(GameTestHelper helper) {
            var player = player(helper, "switch");
            var regenId = com.otectus.arsnspells.bridge.AnsModifierIdentities.uuid(com.otectus.arsnspells.contract.AnsModifierIds.ARS_POTION_MANA_REGEN);
            var maxId = com.otectus.arsnspells.bridge.AnsModifierIdentities.uuid(com.otectus.arsnspells.contract.AnsModifierIds.ARS_POTION_MAX_MANA);
            var regen = player.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MANA_REGEN.get());
            var max = player.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
            // Ars Nouveau 4.12.7 registers no mana_boost effect (LibPotions), so only Mana Regen can be applied;
            // the capacity transfer is checked by its absence outside ISS_PRIMARY.
            try {
                inMode(helper, "iss_primary", () -> {
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(regenPotion(), 400, 0));
                    com.otectus.arsnspells.equipment.PotionContributions.reconcile(player);
                    helper.assertTrue(regen.getModifier(regenId) != null, "ISS_PRIMARY carries the regen");
                });
                inMode(helper, "hybrid", () -> {
                    com.otectus.arsnspells.equipment.PotionContributions.reconcile(player);
                    helper.assertTrue(regen.getModifier(regenId) != null, "HYBRID keeps the regen transfer");
                    helper.assertTrue(max.getModifier(maxId) == null, "and drops the capacity one, which its ceiling already holds");
                    long owned = regen.getModifiers().stream().filter(m -> m.getId().equals(regenId)).count();
                    helper.assertTrue(owned == 1, "one owned regen modifier, not " + owned);
                    player.removeAllEffects(); // the potion expires
                    com.otectus.arsnspells.equipment.PotionContributions.reconcile(player);
                    helper.assertTrue(regen.getModifier(regenId) == null && max.getModifier(maxId) == null,
                        "an expired potion leaves no stale modifier");
                });
            } finally {
                player.removeAllEffects();
                com.otectus.arsnspells.bridge.AnsFeatureCleanup.removeAll(player);
            }
        }
    }
}
