package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.NativeManaAccess;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.equipment.EquipmentIntegration;
import com.otectus.arsnspells.util.ManaUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * 3.3.5: in ARS_PRIMARY the Ars pool is authoritative, and selecting a different hotbar item
 * without casting must never lower it while its true maximum is unchanged.
 *
 * <p>Each assertion reads the native Ars balance on the server, not a HUD value. The hotbar
 * tests drive the real player tick so vanilla's own equipment detection runs, including its
 * order: Forge posts the change event before the attribute modifiers are swapped.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class ManaStabilityGameTests {
    private ManaStabilityGameTests() {}

    private static final UUID BOOST_ID = UUID.fromString("0a000000-0000-0000-0000-0000000b0057");

    @GameTest(template = "platform", batch = "ans_mana_stability")
    public static void ironsLoaded_arsPrimaryIronsRegenCannotLowerArsPool(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        inArsPrimary(() -> Loaded.regenGuard(helper));
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_mana_stability")
    public static void ironsLoaded_arsPrimaryHotbarScrollKeepsAuthoritativeBalance(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        inArsPrimary(() -> Loaded.hotbarCycle(helper));
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_mana_stability")
    public static void ironsLoaded_arsPrimaryRemovedMaxBonusClampsOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        inArsPrimary(() -> Loaded.bonusRemoval(helper));
        helper.succeed();
    }

    private static void inArsPrimary(Runnable body) {
        String mode = AnsConfig.MANA_UNIFICATION_MODE.get();
        boolean unified = AnsConfig.ENABLE_MANA_UNIFICATION.get();
        boolean armor = AnsConfig.respectArmorBonuses.get();
        try {
            TestConfig.set(AnsConfig.ENABLE_MANA_UNIFICATION, true);
            TestConfig.set(AnsConfig.respectArmorBonuses, true);
            TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, "ars_primary");
            BridgeManager.refreshMode();
            body.run();
        } finally {
            TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, mode);
            TestConfig.set(AnsConfig.ENABLE_MANA_UNIFICATION, unified);
            TestConfig.set(AnsConfig.respectArmorBonuses, armor);
            BridgeManager.refreshMode();
        }
    }

    static double ars(ServerPlayer player) {
        return BridgeManager.getNativeArsBridge().transactionMana(player);
    }

    static double arsMax(ServerPlayer player) {
        return BridgeManager.getNativeArsBridge().transactionMax(player);
    }

    static void setArs(ServerPlayer player, int max, double current) {
        NativeManaAccess.with(player, ResourceUnit.ARS_MANA, () -> {
            var cap = ManaUtil.getNativeMana(player).orElseThrow(IllegalStateException::new);
            if (max > 0) cap.setMaxMana(max);
            cap.setMana(current);
            return null;
        });
    }

    /** Ars's own periodic recalculation of its maximum, run now instead of on its schedule. */
    static int recalculateArsMax(ServerPlayer player) {
        int max = com.hollingsworth.arsnouveau.api.util.ManaUtil.getMaxMana(player);
        NativeManaAccess.with(player, ResourceUnit.ARS_MANA, () -> {
            ManaUtil.getNativeMana(player).orElseThrow(IllegalStateException::new).setMaxMana(max);
            return null;
        });
        return max;
    }

    private static final class Loaded {
        private static io.redspace.ironsspellbooks.api.magic.MagicData data(ServerPlayer player) {
            return io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player);
        }

        /**
         * A FakePlayer whose {@code doTick} runs the real player tick: vanilla equipment
         * detection (event first, attribute swap second) and both PlayerTickEvent phases.
         * Its network handler discards packets, so no client connection is needed.
         */
        private static ServerPlayer tickingPlayer(GameTestHelper helper, String scenario) {
            ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, scenario);
            data(player).setServerPlayer(player);
            data(player).resetCastingState();
            for (int slot = 0; slot < 9; slot++) player.getInventory().setItem(slot, ItemStack.EMPTY);
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
            return player;
        }

        private static void regen(ServerPlayer player) {
            ((io.redspace.ironsspellbooks.capabilities.magic.MagicManager)
                io.redspace.ironsspellbooks.api.magic.MagicHelper.MAGIC_MANAGER).regenPlayerMana(player, data(player));
        }

        private static net.minecraft.world.entity.ai.attributes.AttributeInstance ironsMax(ServerPlayer player) {
            return player.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
        }

        /** Iron's regeneration against a mirror that lags, truncates, and one that is aligned. */
        static void regenGuard(GameTestHelper helper) {
            ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, "stability_regen");
            data(player).setServerPlayer(player);
            data(player).resetCastingState();
            setArs(player, 100, 80);
            BridgeManager.getNativeIronsBridge().setMana(player, 15);
            EquipmentIntegration.clearArsBonusesFromIrons(player);
            ironsMax(player).setBaseValue(20);
            regen(player);
            helper.assertTrue(Math.abs(ars(player) - 80) < 1e-6,
                "a lagging Iron's mirror (20) must not clamp the Ars pool (80): got " + ars(player));
            helper.assertTrue(Math.abs(BridgeManager.getNativeIronsBridge().transactionMana(player) - 15) < 1e-6,
                "Iron's regeneration is routed to Ars in ARS_PRIMARY; the dormant Iron's pool must not move");

            setArs(player, 100, 100);
            ironsMax(player).setBaseValue(99.5);
            regen(player);
            helper.assertTrue(Math.abs(ars(player) - 100) < 1e-6,
                "a full Ars pool must not lose a point to Iron's integer-truncated mirror: got " + ars(player));

            setArs(player, 100, 50);
            ironsMax(player).setBaseValue(200);
            regen(player);
            helper.assertTrue(ars(player) > 50 && ars(player) <= 100,
                "Iron's regeneration must still raise the routed Ars pool within Ars's own ceiling: got " + ars(player));
        }

        /**
         * Sixty hotbar selections across an empty hand, a sword, the Ars novice spell book, an
         * Iron's spellbook and a plain item, with Iron's regeneration run after every step. The
         * true maximum never changes, so the authoritative balance may rise but never fall.
         */
        static void hotbarCycle(GameTestHelper helper) {
            ServerPlayer player = tickingPlayer(helper, "stability_hotbar");
            try {
                var novice = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ars_nouveau", "novice_spell_book"));
                helper.assertTrue(novice != null && novice != Items.AIR, "Ars novice spell book must be registered");
                ItemStack[] hotbar = {ItemStack.EMPTY, new ItemStack(Items.IRON_SWORD), new ItemStack(novice),
                    new ItemStack(CrossCastGameTests.findIronsSpellBook()), new ItemStack(Items.STICK)};
                for (int slot = 0; slot < hotbar.length; slot++) player.getInventory().setItem(slot, hotbar[slot]);
                player.getInventory().selected = 0;
                player.doTick();
                int max = recalculateArsMax(player);
                for (double start : new double[]{max * 0.8, max}) {
                    setArs(player, 0, start);
                    BridgeManager.getNativeIronsBridge().setMana(player, 40);
                    double previous = ars(player);
                    for (int step = 0; step < 60; step++) {
                        player.getInventory().selected = step % hotbar.length;
                        player.doTick();
                        regen(player);
                        double now = ars(player);
                        helper.assertTrue(now >= previous - 1e-6, "hotbar step " + step + " (slot " + (step % hotbar.length)
                            + ", start " + start + ") debited the Ars pool: " + previous + " -> " + now);
                        previous = now;
                    }
                    helper.assertTrue(Math.abs(recalculateArsMax(player) - max) < 1e-6,
                        "these items carry no mana bonus; the true Ars maximum must be unchanged");
                    helper.assertTrue(ironsMax(player).getValue() >= max,
                        "the reconciled Iron's mirror must cover the Ars maximum: " + ironsMax(player).getValue() + " < " + max);
                }
            } finally {
                for (int slot = 0; slot < 9; slot++) player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }

        /**
         * An item that really raises the Ars maximum: taking it away clamps the balance to the new
         * maximum once, and holding it again does not take a second bite.
         */
        static void bonusRemoval(GameTestHelper helper) {
            ServerPlayer player = tickingPlayer(helper, "stability_bonus");
            try {
                ItemStack boosted = new ItemStack(Items.STICK);
                boosted.addAttributeModifier(com.hollingsworth.arsnouveau.api.perk.PerkAttributes.MAX_MANA.get(),
                    new AttributeModifier(BOOST_ID, "ANS test max mana", 50, AttributeModifier.Operation.ADDITION), EquipmentSlot.MAINHAND);
                player.getInventory().setItem(0, boosted);
                player.getInventory().setItem(1, ItemStack.EMPTY);
                player.getInventory().selected = 0;
                player.doTick();
                int boostedMax = recalculateArsMax(player);
                player.getInventory().selected = 1;
                player.doTick();
                int plainMax = recalculateArsMax(player);
                helper.assertTrue(boostedMax - plainMax == 50, "fixture must raise the Ars maximum by 50: " + boostedMax + " vs " + plainMax);

                player.getInventory().selected = 0;
                player.doTick();
                recalculateArsMax(player);
                setArs(player, 0, boostedMax - 10);
                player.getInventory().selected = 1;
                player.doTick();
                recalculateArsMax(player);
                regen(player);
                helper.assertTrue(ars(player) >= boostedMax - 10 - 1e-6,
                    "Iron's regeneration must not be the thing that clamps; got " + ars(player));
                // Ars's own next write enforces its maximum, exactly once.
                NativeManaAccess.with(player, ResourceUnit.ARS_MANA, () ->
                    ManaUtil.getNativeMana(player).orElseThrow(IllegalStateException::new).addMana(0));
                helper.assertTrue(Math.abs(ars(player) - plainMax) < 1e-6,
                    "removing a real bonus clamps to the new true maximum: " + ars(player) + " vs " + plainMax);
                player.getInventory().selected = 0;
                player.doTick();
                recalculateArsMax(player);
                helper.assertTrue(ars(player) >= plainMax - 1e-6, "holding the bonus again must not debit: " + ars(player));
                player.getInventory().selected = 1;
                player.doTick();
                recalculateArsMax(player);
                helper.assertTrue(ars(player) >= plainMax - 1e-6, "an unchanged maximum must not be clamped again: " + ars(player));
                helper.assertTrue(ironsMax(player).getValue() >= plainMax, "the Iron's mirror follows the true Ars maximum");
            } finally {
                for (int slot = 0; slot < 9; slot++) player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
    }
}
