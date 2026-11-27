package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.IronsCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Audit ANS-011: the cross-system regen conversion, measured against both mods' native schedulers.
 *
 * <p>Each scenario runs a fixed window of real server ticks (20 at Ars's default interval; the least
 * common multiple of the Ars interval and Iron's 10-tick cadence otherwise, so both schedulers fire a
 * whole number of times) and records the real mana each scheduler added:
 * <ul>
 *   <li>Ars: the real {@code ManaCapEvents.playerOnTick} handler on the real Forge bus, for the START
 *       and END phases of every tick (Ars applies no phase filter), with its own game-time interval;</li>
 *   <li>Iron's: {@code MagicManager.regenPlayerMana} whenever {@code server.getTickCount() % 10 == 0},
 *       the predicate {@code MagicManager.tick} uses. Test players are not in the level's player list,
 *       so the call is driven at that predicate rather than by the level tick itself.</li>
 * </ul>
 * A bonus's effect is the delta of a player with it minus a control player without it, so the base
 * regeneration cancels out. In each direction the bridged bonus (from {@code ManaRegenBridge}, at a
 * non-unit pool conversion rate) must deliver the native bonus's real mana times that rate. Every
 * pool stays far below its ceiling, so no clamp can hide a wrong rate; the ceiling headroom is logged.
 *
 * <p>Iron's-only. Every scenario changes global native settings, so each runs in its own batch.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class RegenCadenceGameTests {
    private RegenCadenceGameTests() {}

    @GameTest(template = "platform", batch = "ans_regen_default", timeoutTicks = 400)
    public static void ironsLoaded_regen_defaultSchedulesBridgeEqualEffect(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.measure(helper, "default", 5, 1.0, false);
    }

    @GameTest(template = "platform", batch = "ans_regen_irons_half", timeoutTicks = 400)
    public static void ironsLoaded_regen_ironsMultiplierHalfBridgesEqualEffect(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.measure(helper, "irons_x0.5", 5, 0.5, false);
    }

    @GameTest(template = "platform", batch = "ans_regen_irons_double", timeoutTicks = 400)
    public static void ironsLoaded_regen_ironsMultiplierDoubleBridgesEqualEffect(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.measure(helper, "irons_x2", 5, 2.0, false);
    }

    @GameTest(template = "platform", batch = "ans_regen_ars_interval3", timeoutTicks = 400)
    public static void ironsLoaded_regen_arsIntervalThreeBridgesEqualEffect(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.measure(helper, "ars_interval_3", 3, 1.0, false);
    }

    @GameTest(template = "platform", batch = "ans_regen_ars_interval7", timeoutTicks = 400)
    public static void ironsLoaded_regen_arsIntervalSevenBridgesEqualEffect(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.measure(helper, "ars_interval_7", 7, 1.0, false);
    }

    @GameTest(template = "platform", batch = "ans_regen_reference_pool", timeoutTicks = 400)
    public static void ironsLoaded_regen_referencePoolAtTheActualPoolBridgesEqualEffect(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.measure(helper, "reference_pool", 5, 2.0, true);
    }

    @GameTest(template = "platform", batch = "ans_regen_irons_disabled", timeoutTicks = 400)
    public static void ironsLoaded_regen_ironsMultiplierZeroBridgesNothing(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.disabled(helper);
    }

    @GameTest(template = "platform", batch = "ans_regen_online_change", timeoutTicks = 100)
    public static void ironsLoaded_regen_nativeConfigChangeRecomputesWithoutAccumulation(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.onlineChange(helper);
    }

    /** Iron's- and Ars-typed bodies, so the GameTest scanner never links Iron's classes on the Iron's-absent profile. */
    private static final class Loaded {
        private static final java.util.UUID BONUS = java.util.UUID.fromString("0a000000-0000-0000-0000-00000000a011");
        private static final java.util.UUID POOL = java.util.UUID.fromString("0a000000-0000-0000-0000-00000000a012");
        private static final double ARS_BONUS = 8.0;
        private static final double IRONS_BONUS = 0.75;
        private static final double RATE_ARS_TO_IRON = 2.5;
        private static final double RATE_IRON_TO_ARS = 0.4;
        private static final double TOLERANCE = 0.01;

        private record Saved(String mode, double arsToIron, double ironToArs, String conversion, double referencePool,
                             double crossMultiplier, int arsInterval, double ironsMultiplier) {}

        private static Saved save() {
            return new Saved(com.otectus.arsnspells.config.AnsConfig.MANA_UNIFICATION_MODE.get(),
                com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get(),
                com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get(),
                com.otectus.arsnspells.config.AnsConfig.CROSS_SYSTEM_REGEN_CONVERSION.get(),
                com.otectus.arsnspells.config.AnsConfig.CROSS_SYSTEM_REGEN_REFERENCE_POOL.get(),
                com.otectus.arsnspells.config.AnsConfig.CROSS_SYSTEM_REGEN_MULTIPLIER.get(),
                com.hollingsworth.arsnouveau.setup.config.ServerConfig.REGEN_INTERVAL.get(),
                io.redspace.ironsspellbooks.config.ServerConfigs.MANA_REGEN_MULTIPLIER.get());
        }

        private static void restore(Saved saved) {
            TestConfig.set(com.otectus.arsnspells.config.AnsConfig.MANA_UNIFICATION_MODE, saved.mode());
            TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_ARS_TO_IRON, saved.arsToIron());
            TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_IRON_TO_ARS, saved.ironToArs());
            TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CROSS_SYSTEM_REGEN_CONVERSION, saved.conversion());
            TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CROSS_SYSTEM_REGEN_REFERENCE_POOL, saved.referencePool());
            TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CROSS_SYSTEM_REGEN_MULTIPLIER, saved.crossMultiplier());
            TestConfig.set(com.hollingsworth.arsnouveau.setup.config.ServerConfig.REGEN_INTERVAL, saved.arsInterval());
            TestConfig.set(io.redspace.ironsspellbooks.config.ServerConfigs.MANA_REGEN_MULTIPLIER, saved.ironsMultiplier());
            com.otectus.arsnspells.bridge.BridgeManager.refreshMode();
        }

        private static net.minecraft.server.level.ServerPlayer player(GameTestHelper helper, String name) {
            var player = CrossCastGameTests.scenarioPlayer(helper, "regen_" + name);
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.removeAllEffects();
            removeModifier(player, com.hollingsworth.arsnouveau.api.perk.PerkAttributes.MANA_REGEN_BONUS.get(), BONUS);
            removeModifier(player, io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MANA_REGEN.get(), BONUS);
            // Ars: a pool far above anything the window can add, starting empty.
            addModifier(player, com.hollingsworth.arsnouveau.api.perk.PerkAttributes.MAX_MANA.get(), POOL, 100000);
            com.otectus.arsnspells.util.ManaUtil.getNativeMana(player).ifPresent(cap -> {
                cap.setGlyphBonus(0);
                cap.setBookTier(0);
            });
            int arsMax = com.hollingsworth.arsnouveau.api.util.ManaUtil.calcMaxMana(player).getRealMax();
            setArs(player, arsMax, 0.0);
            // Iron's: the same, on its own pool.
            addModifier(player, io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get(), POOL, 20000);
            var data = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player);
            data.setServerPlayer(player);
            data.setMana(0.0f);
            return player;
        }

        private static void setArs(net.minecraft.server.level.ServerPlayer player, int max, double mana) {
            com.otectus.arsnspells.bridge.NativeManaAccess.with(player, com.otectus.arsnspells.contract.ResourceUnit.ARS_MANA, () -> {
                var cap = com.otectus.arsnspells.util.ManaUtil.getNativeMana(player).orElseThrow(IllegalStateException::new);
                cap.setMaxMana(max);
                cap.setMana(mana);
                return null;
            });
        }

        private static double ars(net.minecraft.server.level.ServerPlayer player) {
            return com.otectus.arsnspells.bridge.NativeManaAccess.with(player, com.otectus.arsnspells.contract.ResourceUnit.ARS_MANA,
                () -> com.otectus.arsnspells.util.ManaUtil.getNativeMana(player).orElseThrow(IllegalStateException::new).getCurrentMana());
        }

        private static double arsMax(net.minecraft.server.level.ServerPlayer player) {
            return com.otectus.arsnspells.bridge.NativeManaAccess.with(player, com.otectus.arsnspells.contract.ResourceUnit.ARS_MANA,
                () -> (double) com.otectus.arsnspells.util.ManaUtil.getNativeMana(player).orElseThrow(IllegalStateException::new).getMaxMana());
        }

        private static double irons(net.minecraft.server.level.ServerPlayer player) {
            return io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player).getMana();
        }

        private static double ironsMax(net.minecraft.server.level.ServerPlayer player) {
            return player.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
        }

        private static void addModifier(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                        java.util.UUID id, double amount) {
            var instance = player.getAttribute(attribute);
            if (instance == null) throw new IllegalStateException("missing attribute " + attribute);
            instance.removeModifier(id);
            instance.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, "ANS regen cadence test", amount,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        }

        private static void removeModifier(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                           java.util.UUID id) {
            var instance = player.getAttribute(attribute);
            if (instance != null) instance.removeModifier(id);
        }

        private static int window(int arsInterval) {
            int lcm = arsInterval * 10 / gcd(arsInterval, 10);
            int window = lcm;
            while (window < 20) window += lcm;
            return window;
        }

        private static int gcd(int a, int b) {
            return b == 0 ? a : gcd(b, a % b);
        }

        static void measure(GameTestHelper helper, String scenario, int arsInterval, double ironsMultiplier, boolean referencePool) {
            Saved saved = save();
            try {
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.MANA_UNIFICATION_MODE, "disabled");
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_ARS_TO_IRON, RATE_ARS_TO_IRON);
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_IRON_TO_ARS, RATE_IRON_TO_ARS);
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CROSS_SYSTEM_REGEN_MULTIPLIER, 1.0);
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CROSS_SYSTEM_REGEN_CONVERSION, referencePool ? "REFERENCE_POOL" : "EQUAL_EFFECT");
                TestConfig.set(com.hollingsworth.arsnouveau.setup.config.ServerConfig.REGEN_INTERVAL, arsInterval);
                TestConfig.set(io.redspace.ironsspellbooks.config.ServerConfigs.MANA_REGEN_MULTIPLIER, ironsMultiplier);
                com.otectus.arsnspells.bridge.BridgeManager.refreshMode();
            } catch (RuntimeException setup) {
                restore(saved);
                throw setup;
            }
            var arsControl = player(helper, scenario + "_ac");
            var arsBonus = player(helper, scenario + "_ab");
            var arsBridged = player(helper, scenario + "_ax");
            var ironsControl = player(helper, scenario + "_ic");
            var ironsBridged = player(helper, scenario + "_ix");
            var ironsBonus = player(helper, scenario + "_ib");
            if (referencePool) {
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CROSS_SYSTEM_REGEN_REFERENCE_POOL, ironsMax(ironsBridged));
            }
            // Ars -> Iron's: an Ars bonus, and the Iron's attribute the bridge says is worth it at the rate.
            addModifier(arsBonus, com.hollingsworth.arsnouveau.api.perk.PerkAttributes.MANA_REGEN_BONUS.get(), BONUS, ARS_BONUS);
            double bridgedAttr = com.otectus.arsnspells.bridge.ManaRegenBridge.convertArsToIrons(ARS_BONUS * RATE_ARS_TO_IRON, ironsBridged);
            addModifier(ironsBridged, io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MANA_REGEN.get(), BONUS, bridgedAttr);
            // Iron's -> Ars: an Iron's bonus, and the Ars bonus the bridge says is worth it at the rate.
            addModifier(ironsBonus, io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MANA_REGEN.get(), BONUS, IRONS_BONUS);
            double bridgedNominal = com.otectus.arsnspells.bridge.ManaRegenBridge.convertIronsToArs(IRONS_BONUS, ironsBonus) * RATE_IRON_TO_ARS;
            addModifier(arsBridged, com.hollingsworth.arsnouveau.api.perk.PerkAttributes.MANA_REGEN_BONUS.get(), BONUS, bridgedNominal);

            var arsPlayers = java.util.List.of(arsControl, arsBonus, arsBridged);
            var ironsPlayers = java.util.List.of(ironsControl, ironsBridged, ironsBonus);
            int window = window(arsInterval);
            int[] ticks = {0, 0, 0}; // ticks run, Ars-eligible ticks, Iron's calls
            var server = helper.getLevel().getServer();
            helper.onEachTick(() -> {
                if (ticks[0] >= window) return;
                if (helper.getLevel().getGameTime() % Math.max(1, arsInterval) == 0) ticks[1]++;
                for (var player : arsPlayers) {
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                        new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.START, player));
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                        new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END, player));
                }
                if (server.getTickCount() % io.redspace.ironsspellbooks.capabilities.magic.MagicManager.MANA_REGEN_TICKS == 0) {
                    ticks[2]++;
                    for (var player : ironsPlayers)
                        ((io.redspace.ironsspellbooks.capabilities.magic.MagicManager) io.redspace.ironsspellbooks.api.magic.MagicHelper.MAGIC_MANAGER).regenPlayerMana(player,
                            io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player));
                }
                ticks[0]++;
            });
            helper.runAfterDelay(window + 3, () -> {
                try {
                    double arsNative = ars(arsBonus) - ars(arsControl);
                    double arsFromBridge = ars(arsBridged) - ars(arsControl);
                    double ironsNative = irons(ironsBonus) - irons(ironsControl);
                    double ironsFromBridge = irons(ironsBridged) - irons(ironsControl);
                    double headroom = Math.min(Math.min(arsMax(arsBonus) - ars(arsBonus), arsMax(arsBridged) - ars(arsBridged)),
                        Math.min(ironsMax(ironsBridged) - irons(ironsBridged), ironsMax(ironsBonus) - irons(ironsBonus)));
                    System.out.println("ANS_REGEN_MEASURE scenario=" + scenario + " window=" + window + " arsInterval=" + arsInterval
                        + " ironsMultiplier=" + ironsMultiplier + " arsEligibleTicks=" + ticks[1] + " arsHandlerCalls=" + (ticks[1] * 2)
                        + " ironsCalls=" + ticks[2] + " arsControlDelta=" + ars(arsControl) + " ironsControlDelta=" + irons(ironsControl)
                        + " arsBonusDelta=" + arsNative + " ironsFromBridgedAttr(" + bridgedAttr + ")=" + ironsFromBridge
                        + " ironsBonusDelta=" + ironsNative + " arsFromBridgedNominal(" + bridgedNominal + ")=" + arsFromBridge
                        + " minHeadroom=" + headroom);
                    helper.assertTrue(ticks[0] == window, "the window ran " + ticks[0] + " of " + window + " ticks");
                    helper.assertTrue(headroom > 1000, "every pool stayed far below its ceiling, headroom " + headroom);
                    helper.assertTrue(arsNative > 0 && ironsNative > 0, "both native bonuses regenerate: " + arsNative + ", " + ironsNative);
                    double expectedIrons = RATE_ARS_TO_IRON * arsNative;
                    helper.assertTrue(Math.abs(ironsFromBridge - expectedIrons) <= TOLERANCE * expectedIrons,
                        scenario + ": the bridged Iron's attribute must deliver " + expectedIrons + " (rate x native Ars bonus " + arsNative
                            + "), delivered " + ironsFromBridge);
                    double expectedArs = RATE_IRON_TO_ARS * ironsNative;
                    helper.assertTrue(Math.abs(arsFromBridge - expectedArs) <= TOLERANCE * expectedArs,
                        scenario + ": the bridged Ars bonus must deliver " + expectedArs + " (rate x native Iron's bonus " + ironsNative
                            + "), delivered " + arsFromBridge);
                } finally {
                    restore(saved);
                }
                helper.succeed();
            });
        }

        static void disabled(GameTestHelper helper) {
            Saved saved = save();
            try {
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CROSS_SYSTEM_REGEN_CONVERSION, "EQUAL_EFFECT");
                TestConfig.set(io.redspace.ironsspellbooks.config.ServerConfigs.MANA_REGEN_MULTIPLIER, 0.0);
                var player = player(helper, "disabled");
                double toIrons = com.otectus.arsnspells.bridge.ManaRegenBridge.convertArsToIrons(ARS_BONUS, player);
                double toArs = com.otectus.arsnspells.bridge.ManaRegenBridge.convertIronsToArs(IRONS_BONUS, player);
                helper.assertTrue(Double.isFinite(toIrons) && toIrons == 0.0,
                    "with Iron's native regen disabled no attribute can carry Ars regen across, got " + toIrons);
                helper.assertTrue(Double.isFinite(toArs) && toArs == 0.0, "and Iron's regen is worth nothing in Ars, got " + toArs);
            } finally {
                restore(saved);
            }
            helper.succeed();
        }

        /** Iron's multiplier changed while online: the owned potion modifier is recomputed in place, never stacked. */
        static void onlineChange(GameTestHelper helper) {
            var effect = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getOptional(
                new net.minecraft.resources.ResourceLocation("ars_nouveau", "mana_regen")).orElseThrow();
            Saved saved = save();
            var player = player(helper, "online");
            var id = com.otectus.arsnspells.bridge.AnsModifierIdentities.uuid(com.otectus.arsnspells.contract.AnsModifierIds.ARS_POTION_MANA_REGEN);
            var regen = player.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MANA_REGEN.get());
            try {
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.MANA_UNIFICATION_MODE, "iss_primary");
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CROSS_SYSTEM_REGEN_CONVERSION, "EQUAL_EFFECT");
                TestConfig.set(io.redspace.ironsspellbooks.config.ServerConfigs.MANA_REGEN_MULTIPLIER, 1.0);
                com.otectus.arsnspells.bridge.BridgeManager.refreshMode();
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, 200, 0));
                com.otectus.arsnspells.equipment.PotionContributions.reconcile(player);
                var first = regen.getModifier(id);
                helper.assertTrue(first != null && first.getAmount() > 0, "the potion is mirrored at multiplier 1");
                TestConfig.set(io.redspace.ironsspellbooks.config.ServerConfigs.MANA_REGEN_MULTIPLIER, 2.0);
                com.otectus.arsnspells.equipment.PotionContributions.reconcile(player);
                com.otectus.arsnspells.equipment.PotionContributions.reconcile(player);
                var second = regen.getModifier(id);
                long owned = regen.getModifiers().stream().filter(m -> m.getId().equals(id)).count();
                helper.assertTrue(owned == 1, "one owned modifier, not " + owned);
                helper.assertTrue(second != null && Math.abs(second.getAmount() - first.getAmount() / 2.0) < 1.0e-9,
                    "Iron's delivering twice as much per point halves the mirrored attribute: " + first.getAmount() + " -> "
                        + (second == null ? "nothing" : second.getAmount()));
            } finally {
                player.removeAllEffects();
                com.otectus.arsnspells.bridge.AnsFeatureCleanup.removeAll(player);
                restore(saved);
            }
            helper.succeed();
        }
    }
}
