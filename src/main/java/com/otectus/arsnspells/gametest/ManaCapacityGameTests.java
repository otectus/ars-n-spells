package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.IronsCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 3.3.7: the size of the unified pool in ISS_PRIMARY.
 *
 * <p>Iron's {@code max_mana} is the pool's capacity in ISS_PRIMARY: Iron's clamps every write to
 * it and its HUD shows it. ANS raises it to {@code max(Ars max x rate, Iron's own max + Ars gear x
 * rate)}. The NeoForge 3.3.5 parity change dropped the first term, so a player whose Ars
 * progression (base pool, glyphs, book tier) outgrew their Iron's pool lost the difference: the
 * report was 740 on 3.3.4 and 350 on 3.3.5 and 3.3.6. The Forge build did not have the first term
 * before 3.3.7; both builds now apply the same rule.
 *
 * <p>Every scenario reads server-authoritative values after the real player tick has run
 * vanilla's equipment detection, Curios' attribute pass and ANS's tick-end reconciliation, and
 * measures capacity by filling the authoritative pool, not by reading a HUD value. The fixture
 * Ars pool is 100 base + 16 glyphs x 15 + book tier 3 x 50 = 490, plus 250 from an Ars helmet.
 *
 * <p>Iron's-only. The Iron's-typed bodies live in {@link Loaded} so the GameTest scanner never
 * links Iron's classes on the Iron's-absent profile.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class ManaCapacityGameTests {
    private ManaCapacityGameTests() {}

    @GameTest(template = "platform", batch = "ans_mana_capacity")
    public static void ironsLoaded_issPrimary_arsProgressionFloorsTheSharedPool(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.progressionFloor(helper);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_mana_capacity")
    public static void ironsLoaded_issPrimary_ironsSideKeepsTheArsGearBonus(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.ironsSide(helper);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_mana_capacity")
    public static void ironsLoaded_issPrimary_multiplicativeModifiersConverge(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.multiplicative(helper);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_mana_capacity")
    public static void ironsLoaded_issPrimary_hotbarAndBonusRemoval(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.hotbar(helper);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_mana_capacity")
    public static void ironsLoaded_issPrimary_curioContributionsFollowTheToggles(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.curios(helper);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_mana_capacity")
    public static void ironsLoaded_issPrimary_lifecycleRestoresTheFloor(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.lifecycle(helper);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_mana_capacity")
    public static void ironsLoaded_issPrimary_existingWorldRecoversWithoutReequipping(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.upgrade(helper);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_mana_capacity")
    public static void ironsLoaded_issPrimary_castsAndRegenUseTheRestoredPool(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.castingAndRegen(helper);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_mana_capacity")
    public static void ironsLoaded_otherModesKeepTheirCeilings(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.otherModes(helper);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_mana_capacity")
    public static void ironsLoaded_issPrimary_ceilingChangeIsQueuedForTheClient(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.clientSync(helper);
        helper.succeed();
    }

    private static final class Loaded {
        private static final double EPSILON = 0.01;
        private static final int GLYPHS = 16;
        private static final int TIER = 3;
        private static final double ARS_HELMET = 250.0;
        /** 100 base + 16 x 15 + 3 x 50, under Ars's default server config. */
        private static final double ARS_PROGRESSION = 490.0;

        private static final java.util.UUID ARS_GEAR = id("ars_gear");
        private static final java.util.UUID ARS_HELD = id("ars_held");
        private static final java.util.UUID IRONS_GEAR = id("irons_gear");
        private static final java.util.UUID IRONS_MULTIPLY_BASE = id("irons_multiply_base");
        private static final java.util.UUID IRONS_MULTIPLY_TOTAL = id("irons_multiply_total");
        private static final java.util.UUID ARS_CURIO = id("ars_curio");
        private static final java.util.UUID IRONS_CURIO = id("irons_curio");
        private static final java.util.UUID PROBE = id("amplification_probe");

        private static java.util.UUID id(String name) {
            return java.util.UUID.nameUUIDFromBytes(("ans_capacity_fixture:" + name)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        // ------------------------------------------------------------------ fixture plumbing

        private static net.minecraft.world.entity.ai.attributes.Attribute ironsMaxAttribute() {
            return io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get();
        }

        private static net.minecraft.world.entity.ai.attributes.Attribute arsMaxAttribute() {
            return com.hollingsworth.arsnouveau.api.perk.PerkAttributes.MAX_MANA.get();
        }

        private static net.minecraft.world.entity.ai.attributes.AttributeInstance ironsMax(
                net.minecraft.server.level.ServerPlayer player) {
            return player.getAttribute(ironsMaxAttribute());
        }

        private static io.redspace.ironsspellbooks.api.magic.MagicData data(net.minecraft.server.level.ServerPlayer player) {
            return io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player);
        }

        private static com.hollingsworth.arsnouveau.api.mana.IManaCap ars(net.minecraft.server.level.ServerPlayer player) {
            return com.otectus.arsnspells.util.ManaUtil.getNativeMana(player).orElseThrow(IllegalStateException::new);
        }

        /** A survival FakePlayer with no gear, no Ars progression, Iron's base 100, no ANS modifier. */
        private static net.minecraft.server.level.ServerPlayer player(GameTestHelper helper, String scenario) {
            var player = CrossCastGameTests.scenarioPlayer(helper, "cap_" + scenario);
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                player.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
            }
            for (int slot = 0; slot < 9; slot++) player.getInventory().setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
            player.getInventory().selected = 0;
            player.removeAllEffects();
            ars(player).setGlyphBonus(0);
            ars(player).setBookTier(0);
            data(player).setServerPlayer(player);
            data(player).resetCastingState();
            ironsMax(player).setBaseValue(100);
            com.otectus.arsnspells.bridge.AnsFeatureCleanup.removeAll(player);
            settle(player);
            return player;
        }

        /**
         * The real player tick twice (vanilla equipment detection, Curios' attribute pass, ANS's
         * tick-end reconciliation), Ars's own login/respawn max sync, and one more tick.
         */
        private static void settle(net.minecraft.server.level.ServerPlayer player) {
            player.doTick();
            player.doTick();
            com.hollingsworth.arsnouveau.common.event.ManaCapEvents.syncPlayerEvent(player);
            player.doTick();
        }

        private static net.minecraft.world.item.ItemStack gear(net.minecraft.world.item.Item item,
                net.minecraft.world.entity.EquipmentSlot slot, Object... modifiers) {
            var stack = new net.minecraft.world.item.ItemStack(item);
            for (int i = 0; i < modifiers.length; i += 2) {
                stack.addAttributeModifier((net.minecraft.world.entity.ai.attributes.Attribute) modifiers[i],
                    (net.minecraft.world.entity.ai.attributes.AttributeModifier) modifiers[i + 1], slot);
            }
            return stack;
        }

        private static net.minecraft.world.entity.ai.attributes.AttributeModifier add(java.util.UUID id, double amount) {
            return new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, "ans capacity fixture", amount,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
        }

        private static net.minecraft.world.entity.ai.attributes.AttributeModifier multiply(java.util.UUID id, double amount,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation operation) {
            return new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, "ans capacity fixture", amount, operation);
        }

        /** Ars glyph bonus, book tier and the Ars helmet: an Ars pool of 740. */
        private static void arsProgression(net.minecraft.server.level.ServerPlayer player) {
            ars(player).setGlyphBonus(GLYPHS);
            ars(player).setBookTier(TIER);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, gear(net.minecraft.world.item.Items.LEATHER_HELMET,
                net.minecraft.world.entity.EquipmentSlot.HEAD, arsMaxAttribute(), add(ARS_GEAR, ARS_HELMET)));
        }

        private static void inMode(String mode, double arsToIron, double ironToArs, Runnable body) {
            String previousMode = com.otectus.arsnspells.config.AnsConfig.MANA_UNIFICATION_MODE.get();
            double previousToIron = com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get();
            double previousToArs = com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get();
            boolean previousArmor = com.otectus.arsnspells.config.AnsConfig.respectArmorBonuses.get();
            boolean previousEnchantments = com.otectus.arsnspells.config.AnsConfig.respectEnchantments.get();
            boolean previousCurios = com.otectus.arsnspells.config.AnsConfig.READ_CURIO_ATTRIBUTE_MODIFIERS.get();
            try {
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.MANA_UNIFICATION_MODE, mode);
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_ARS_TO_IRON, arsToIron);
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_IRON_TO_ARS, ironToArs);
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.respectArmorBonuses, true);
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.respectEnchantments, true);
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.READ_CURIO_ATTRIBUTE_MODIFIERS, true);
                com.otectus.arsnspells.bridge.BridgeManager.refreshMode();
                if (!mode.equals(com.otectus.arsnspells.bridge.BridgeManager.getCurrentMode().getConfigName())) {
                    throw new IllegalStateException("could not enter " + mode + "; got "
                        + com.otectus.arsnspells.bridge.BridgeManager.getCurrentMode());
                }
                body.run();
            } finally {
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.MANA_UNIFICATION_MODE, previousMode);
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_ARS_TO_IRON, previousToIron);
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_IRON_TO_ARS, previousToArs);
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.respectArmorBonuses, previousArmor);
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.respectEnchantments, previousEnchantments);
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.READ_CURIO_ATTRIBUTE_MODIFIERS, previousCurios);
                com.otectus.arsnspells.bridge.BridgeManager.refreshMode();
            }
        }

        private static void inIssPrimary(double rate, Runnable body) {
            inMode("iss_primary", rate, 1.0, body);
        }

        // ------------------------------------------------------------------ measurements

        /** Ars's own computed maximum: base, glyphs, book tier and every Ars max-mana modifier. */
        private static double arsReal(net.minecraft.server.level.ServerPlayer player) {
            return com.hollingsworth.arsnouveau.api.util.ManaUtil.calcMaxMana(player).getRealMax();
        }

        private static java.util.Set<java.util.UUID> ansIds() {
            java.util.Set<java.util.UUID> ids = new java.util.HashSet<>();
            for (String key : com.otectus.arsnspells.contract.AnsModifierIds.allKeys()) {
                for (String identity : com.otectus.arsnspells.contract.AnsModifierIds.currentAndLegacyKeysFor(key)) {
                    java.util.UUID mapped = com.otectus.arsnspells.bridge.AnsModifierIdentities.MAPPER.map(identity);
                    if (mapped != null) ids.add(mapped);
                }
            }
            return ids;
        }

        /** Iron's max with every ANS modifier taken off for the reading, then put back as it was. */
        private static double ironsOwn(net.minecraft.server.level.ServerPlayer player) {
            var instance = ironsMax(player);
            java.util.List<net.minecraft.world.entity.ai.attributes.AttributeModifier> removed = new java.util.ArrayList<>();
            for (var modifier : java.util.List.copyOf(instance.getModifiers())) {
                if (ansIds().contains(modifier.getId())) {
                    removed.add(modifier);
                    instance.removeModifier(modifier.getId());
                }
            }
            double value = instance.getValue();
            removed.forEach(instance::addTransientModifier);
            return value;
        }

        /** What one ADDITION point is worth on Iron's max, measured with a throwaway modifier. */
        private static double amplification(net.minecraft.server.level.ServerPlayer player) {
            var instance = ironsMax(player);
            double before = instance.getValue();
            instance.addTransientModifier(add(PROBE, 1.0));
            double after = instance.getValue();
            instance.removeModifier(PROBE);
            return after - before;
        }

        /** Capacity as the authoritative pool enforces it: write far above any maximum, read back. */
        private static float fill(net.minecraft.server.level.ServerPlayer player) {
            var bridge = com.otectus.arsnspells.bridge.BridgeManager.getBridge();
            bridge.setMana(player, 1.0e6f);
            return bridge.getMana(player);
        }

        private static float pool(net.minecraft.server.level.ServerPlayer player) {
            return com.otectus.arsnspells.bridge.BridgeManager.getBridge().getMana(player);
        }

        private static void regen(net.minecraft.server.level.ServerPlayer player) {
            ((io.redspace.ironsspellbooks.capabilities.magic.MagicManager)
                io.redspace.ironsspellbooks.api.magic.MagicHelper.MAGIC_MANAGER).regenPlayerMana(player, data(player));
        }

        private static void equal(GameTestHelper helper, double expected, double actual, String label) {
            helper.assertTrue(Math.abs(expected - actual) < EPSILON, label + ": expected " + expected + ", got " + actual);
        }

        /** The ISS_PRIMARY capacity rule, written out independently of the production arithmetic. */
        private static double issPrimaryCapacity(double arsMax, double ironsOwn, double amplification, double arsGear,
                                                 double rate) {
            return Math.max(arsMax * rate, ironsOwn + amplification * arsGear * rate);
        }

        private static void assertCapacity(GameTestHelper helper, net.minecraft.server.level.ServerPlayer player,
                                           double expected, String label) {
            equal(helper, expected, ironsMax(player).getValue(), label + ": Iron's max_mana");
            equal(helper, expected, com.otectus.arsnspells.bridge.BridgeManager.getBridge().getMaxMana(player),
                label + ": the authoritative pool's maximum");
            equal(helper, Math.floor(expected), Math.floor(ars(player).getMaxMana()),
                label + ": the Ars cap's routed maximum");
            equal(helper, expected, fill(player), label + ": capacity of the authoritative pool");
        }

        // ------------------------------------------------------------------ scenarios

        /**
         * Size the pool at {@code rate}. The reconciliation tick has already applied the configured
         * rate (1.0); any other rate goes through the same production method directly. These
         * scenarios write no config for it: every server-config write is a file save that the
         * config watcher can replay later, over a value another test has just set.
         */
        private static void sizeAt(net.minecraft.server.level.ServerPlayer player, double rate) {
            if (rate != com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get()) {
                com.otectus.arsnspells.equipment.EquipmentIntegration.applyArsBonusesToIrons(player, rate);
            }
        }

        /** One more reconciliation at {@code rate}: the tick at the configured rate, the direct call otherwise. */
        private static void reconcileAt(net.minecraft.server.level.ServerPlayer player, double rate) {
            if (rate != com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get()) {
                com.otectus.arsnspells.equipment.EquipmentIntegration.applyArsBonusesToIrons(player, rate);
                return;
            }
            com.otectus.arsnspells.equipment.EquipmentIntegration.clearCache(player);
            com.otectus.arsnspells.events.EquipmentHandler.recomputeContributions(player);
            player.doTick();
        }

        /** The report's shape: Ars progression above Iron's own pool, at three conversion rates. */
        static void progressionFloor(GameTestHelper helper) {
            inIssPrimary(1.0, () -> {
                for (double rate : new double[] {0.5, 1.0, 2.0}) {
                    var player = player(helper, "floor_" + rate);
                    arsProgression(player);
                    settle(player);
                    sizeAt(player, rate);
                    equal(helper, ARS_PROGRESSION + ARS_HELMET, arsReal(player), "fixture: Ars's own maximum");
                    equal(helper, 100, ironsOwn(player), "fixture: Iron's own maximum");
                    double expected = issPrimaryCapacity(ARS_PROGRESSION + ARS_HELMET, 100, 1, ARS_HELMET, rate);
                    assertCapacity(helper, player, expected, "ISS_PRIMARY at rate " + rate
                        + " (Ars pool 740 x rate; the gear-only rule gives " + (100 + ARS_HELMET * rate) + ")");
                }
            });
            // The configured rate reaches the pool through the ordinary reconciliation tick.
            inIssPrimary(2.0, () -> {
                var player = player(helper, "floor_configured");
                arsProgression(player);
                settle(player);
                assertCapacity(helper, player, issPrimaryCapacity(ARS_PROGRESSION + ARS_HELMET, 100, 1, ARS_HELMET, 2.0),
                    "ISS_PRIMARY at the configured rate 2.0");
            });
        }

        /** Iron's own pool above the Ars pool: the 3.3.5 gear bonus still adds, times the rate. */
        static void ironsSide(GameTestHelper helper) {
            inIssPrimary(1.0, () -> {
                for (double rate : new double[] {0.5, 1.0, 2.0}) {
                    var player = player(helper, "irons_" + rate);
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, gear(net.minecraft.world.item.Items.LEATHER_HELMET,
                        net.minecraft.world.entity.EquipmentSlot.HEAD, arsMaxAttribute(), add(ARS_GEAR, 100)));
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, gear(net.minecraft.world.item.Items.LEATHER_CHESTPLATE,
                        net.minecraft.world.entity.EquipmentSlot.CHEST, ironsMaxAttribute(), add(IRONS_GEAR, 400)));
                    settle(player);
                    sizeAt(player, rate);
                    equal(helper, 200, arsReal(player), "fixture: Ars's own maximum");
                    equal(helper, 500, ironsOwn(player), "fixture: Iron's own maximum");
                    assertCapacity(helper, player, 500 + 100 * rate,
                        "ISS_PRIMARY at rate " + rate + " keeps the Ars gear bonus on top of Iron's own pool");
                }
            });
        }

        /** Fractional gear and Iron's MULTIPLY_BASE and MULTIPLY_TOTAL; repeated reconciliation is stable. */
        static void multiplicative(GameTestHelper helper) {
            inIssPrimary(1.0, () -> {
                for (double rate : new double[] {0.5, 1.0, 2.0}) {
                    var player = player(helper, "multiply_" + rate);
                    ars(player).setGlyphBonus(7);
                    ars(player).setBookTier(1);
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, gear(net.minecraft.world.item.Items.LEATHER_HELMET,
                        net.minecraft.world.entity.EquipmentSlot.HEAD, arsMaxAttribute(), add(ARS_GEAR, 37.5)));
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, gear(net.minecraft.world.item.Items.LEATHER_CHESTPLATE,
                        net.minecraft.world.entity.EquipmentSlot.CHEST,
                        ironsMaxAttribute(), add(IRONS_GEAR, 60),
                        ironsMaxAttribute(), multiply(IRONS_MULTIPLY_BASE, 0.25,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE),
                        ironsMaxAttribute(), multiply(IRONS_MULTIPLY_TOTAL, 0.1,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL)));
                    settle(player);
                    sizeAt(player, rate);
                    // (int) (100 + 7 x 15 + 50 + 37.5): Ars truncates its maximum to a whole number.
                    equal(helper, 292, arsReal(player), "fixture: Ars's own maximum");
                    equal(helper, 160 * 1.25 * 1.1, ironsOwn(player), "fixture: Iron's own maximum");
                    equal(helper, 1.25 * 1.1, amplification(player), "fixture: Iron's additive amplification");
                    double expected = issPrimaryCapacity(292, 220, 1.375, 37.5, rate);
                    assertCapacity(helper, player, expected, "ISS_PRIMARY with multipliers at rate " + rate);
                    var modifier = ironsMax(player).getModifier(com.otectus.arsnspells.bridge.AnsModifierIdentities.uuid(
                        com.otectus.arsnspells.contract.AnsModifierIds.ARS_GEAR_MAX_MANA));
                    double amount = modifier == null ? 0 : modifier.getAmount();
                    for (int pass = 0; pass < 5; pass++) {
                        reconcileAt(player, rate);
                        var again = ironsMax(player).getModifier(com.otectus.arsnspells.bridge.AnsModifierIdentities.uuid(
                            com.otectus.arsnspells.contract.AnsModifierIds.ARS_GEAR_MAX_MANA));
                        helper.assertTrue(again != null && again.getAmount() == amount,
                            "reconciliation pass " + pass + " moved the ANS modifier from " + amount + " to "
                                + (again == null ? "nothing" : again.getAmount()));
                        equal(helper, expected, ironsMax(player).getValue(), "pass " + pass + ": Iron's max_mana");
                    }
                }
            });
        }

        /** A held Ars max-mana item: the pool follows it, a real removal clamps once, and nothing else debits. */
        static void hotbar(GameTestHelper helper) {
            inIssPrimary(1.0, () -> {
                var player = player(helper, "hotbar");
                ars(player).setGlyphBonus(GLYPHS);
                ars(player).setBookTier(TIER);
                player.getInventory().setItem(0, gear(net.minecraft.world.item.Items.STICK,
                    net.minecraft.world.entity.EquipmentSlot.MAINHAND, arsMaxAttribute(), add(ARS_HELD, 300)));
                player.getInventory().setItem(1, net.minecraft.world.item.ItemStack.EMPTY);
                player.getInventory().setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
                player.getInventory().setItem(3, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK));
                player.getInventory().selected = 0;
                settle(player);
                assertCapacity(helper, player, ARS_PROGRESSION + 300, "holding the Ars item");

                player.getInventory().selected = 1;
                settle(player);
                equal(helper, ARS_PROGRESSION, ironsMax(player).getValue(), "after putting the Ars item away, Iron's max_mana");
                regen(player);
                equal(helper, ARS_PROGRESSION, pool(player), "a removed bonus clamps the pool to the new maximum once");

                for (int step = 0; step < 20; step++) {
                    float before = pool(player);
                    player.getInventory().selected = 2 + step % 2;
                    player.doTick();
                    regen(player);
                    helper.assertTrue(pool(player) >= before - 1.0e-3,
                        "hotbar step " + step + " debited the pool: " + before + " -> " + pool(player));
                    equal(helper, ARS_PROGRESSION, ironsMax(player).getValue(), "hotbar step " + step + ": unchanged maximum");
                }

                float beforeReturn = pool(player);
                player.getInventory().selected = 0;
                settle(player);
                equal(helper, ARS_PROGRESSION + 300, ironsMax(player).getValue(), "holding the Ars item again");
                equal(helper, beforeReturn, pool(player), "raising the maximum adds no mana by itself");
                regen(player);
                helper.assertTrue(pool(player) > beforeReturn && pool(player) <= ARS_PROGRESSION + 300 + 1.0e-3,
                    "regeneration fills towards the restored maximum: " + pool(player));
            });
        }

        /**
         * Curios: an Ars max-mana curio (and, for the second player, an Iron's one as well), with
         * read_curio_attribute_modifiers on and off. The worn book's own Iron's modifiers, if any,
         * are measured rather than assumed.
         */
        static void curios(GameTestHelper helper) {
            java.util.function.Consumer<top.theillusivec4.curios.api.event.CurioAttributeModifierEvent> listener = event -> {
                if (event.getSlotContext().entity() instanceof net.minecraft.server.level.ServerPlayer owner) {
                    String name = owner.getGameProfile().getName();
                    if (name.startsWith("cap_curios")) event.addModifier(arsMaxAttribute(), add(ARS_CURIO, 200));
                    if (name.equals("cap_curios_both")) event.addModifier(ironsMaxAttribute(), add(IRONS_CURIO, 400));
                }
            };
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
            try {
                inIssPrimary(1.0, () -> {
                    String[] scenarios = {"curios_ars", "curios_both"};
                    var players = new net.minecraft.server.level.ServerPlayer[scenarios.length];
                    double[] arsMax = new double[scenarios.length];
                    double[] own = new double[scenarios.length];
                    double[] amplification = new double[scenarios.length];
                    for (int i = 0; i < scenarios.length; i++) {
                        boolean both = scenarios[i].equals("curios_both");
                        var player = players[i] = player(helper, scenarios[i]);
                        ars(player).setGlyphBonus(10);
                        ars(player).setBookTier(2);
                        var book = IronsTableDriver.findNativeInscribableBook();
                        helper.assertTrue(!book.isEmpty(), "a native Iron's spellbook must exist");
                        io.redspace.ironsspellbooks.api.util.Utils.setPlayerSpellbookStack(player, book);
                        settle(player);
                        helper.assertTrue(player.getAttribute(arsMaxAttribute()).getModifier(ARS_CURIO) != null,
                            scenarios[i] + ": Curios must apply the worn curio's Ars modifier during the player tick");
                        helper.assertTrue((ironsMax(player).getModifier(IRONS_CURIO) != null) == both,
                            scenarios[i] + ": the Iron's curio modifier is worn only by the second player");
                        arsMax[i] = arsReal(player);
                        own[i] = ironsOwn(player);
                        amplification[i] = amplification(player);
                        equal(helper, 100 + 150 + 100 + 200, arsMax[i], scenarios[i] + ": Ars's own maximum with the curio");
                        assertCapacity(helper, player, issPrimaryCapacity(arsMax[i], own[i], amplification[i], 200, 1.0),
                            scenarios[i] + ", curio read on");
                    }

                    // One write each way for both players: every config write is a file save.
                    TestConfig.set(com.otectus.arsnspells.config.AnsConfig.READ_CURIO_ATTRIBUTE_MODIFIERS, false);
                    for (int i = 0; i < scenarios.length; i++) {
                        com.otectus.arsnspells.equipment.EquipmentIntegration.clearCache(players[i]);
                        settle(players[i]);
                        // Withheld from the gear bonus and from the Ars floor alike; Iron's own curio stays Iron's.
                        assertCapacity(helper, players[i], issPrimaryCapacity(arsMax[i] - 200, own[i], amplification[i], 0, 1.0),
                            scenarios[i] + ", curio read off");
                    }
                    TestConfig.set(com.otectus.arsnspells.config.AnsConfig.READ_CURIO_ATTRIBUTE_MODIFIERS, true);
                    for (int i = 0; i < scenarios.length; i++) {
                        var player = players[i];
                        com.otectus.arsnspells.equipment.EquipmentIntegration.clearCache(player);
                        settle(player);
                        assertCapacity(helper, player, issPrimaryCapacity(arsMax[i], own[i], amplification[i], 200, 1.0),
                            scenarios[i] + ", curio read on again");

                        io.redspace.ironsspellbooks.api.util.Utils.setPlayerSpellbookStack(player,
                            net.minecraft.world.item.ItemStack.EMPTY);
                        settle(player);
                        equal(helper, 350, arsReal(player), scenarios[i] + ": taking the curio off removes its Ars contribution");
                        assertCapacity(helper, player, issPrimaryCapacity(350, 100, 1, 0, 1.0), scenarios[i] + ", curio removed");
                    }
                });
            } finally {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
            }
        }

        /**
         * Relog, respawn, dimension change and a save/load round trip each rebuild the restored pool,
         * and a relog keeps the saved balance: vanilla applies equipment modifiers only in the
         * player's first tick, so a maximum read at login is missing every gear contribution.
         */
        static void lifecycle(GameTestHelper helper) {
            inIssPrimary(1.0, () -> {
                var player = player(helper, "lifecycle");
                arsProgression(player);
                settle(player);
                double expected = ARS_PROGRESSION + ARS_HELMET;
                assertCapacity(helper, player, expected, "before any lifecycle event");

                // ANS's own lifecycle listeners, handed the real events. Posting them on the bus would
                // also run Forge's login listeners, which need a network channel a FakePlayer lacks.
                java.util.Map<String, Runnable> events = new java.util.LinkedHashMap<>();
                events.put("relog", () -> com.otectus.arsnspells.events.EquipmentHandler.onPlayerLogin(
                    new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(player)));
                events.put("respawn", () -> com.otectus.arsnspells.events.EquipmentHandler.onPlayerRespawn(
                    new net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(player, false)));
                events.put("dimension change", () -> com.otectus.arsnspells.events.EquipmentHandler.onPlayerChangedDimension(
                    new net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(player,
                        net.minecraft.world.level.Level.OVERWORLD, net.minecraft.world.level.Level.NETHER)));
                for (var event : events.entrySet()) {
                    // A rebuilt player entity carries none of ANS's transient modifiers.
                    com.otectus.arsnspells.bridge.AnsFeatureCleanup.removeAll(player);
                    equal(helper, 100, ironsMax(player).getValue(), event.getKey() + ": the ceiling starts without ANS");
                    event.getValue().run();
                    equal(helper, expected, ironsMax(player).getValue(), event.getKey() + ": Iron's max_mana restored");
                }

                // Server restart: save the player, load it into a fresh entity, log it in.
                com.otectus.arsnspells.bridge.BridgeManager.getBridge().setMana(player, 700);
                equal(helper, 700, pool(player), "a balance above the gear-only ceiling of 350");
                net.minecraft.nbt.CompoundTag saved = player.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                saved.remove("UUID"); // the fresh entity keeps its own identity
                var restarted = CrossCastGameTests.scenarioPlayer(helper, "cap_lifecycle_restart");
                restarted.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                com.otectus.arsnspells.bridge.AnsFeatureCleanup.removeAll(restarted);
                restarted.load(saved);
                data(restarted).setServerPlayer(restarted);
                equal(helper, GLYPHS, ars(restarted).getGlyphBonus(), "restart keeps the Ars glyph bonus");
                equal(helper, TIER, ars(restarted).getBookTier(), "restart keeps the Ars book tier");
                equal(helper, 700, data(restarted).getMana(), "restart keeps the saved balance");
                com.otectus.arsnspells.events.EquipmentHandler.onPlayerLogin(
                    new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(restarted));
                equal(helper, 700, data(restarted).getMana(), "logging in must not clamp the saved balance before the gear is on");
                settle(restarted);
                equal(helper, expected, ironsMax(restarted).getValue(), "after restart and login, Iron's max_mana");
                equal(helper, 700, data(restarted).getMana(), "the first tick after login keeps the saved balance");
                regen(restarted);
                helper.assertTrue(data(restarted).getMana() >= 700 - 1.0e-3,
                    "the first regeneration after the restart must not clamp the saved balance: " + data(restarted).getMana());
            });
        }

        /**
         * An existing world after 3.3.5/3.3.6: the pool sits at the gear-only ceiling (350) with the
         * progression and gear unchanged. Normal reconciliation restores the maximum; no re-equip,
         * config edit or progression change, and regeneration refills the difference.
         */
        static void upgrade(GameTestHelper helper) {
            inIssPrimary(1.0, () -> {
                var player = player(helper, "upgrade");
                arsProgression(player);
                settle(player);
                var instance = ironsMax(player);
                java.util.UUID ansId = com.otectus.arsnspells.bridge.AnsModifierIdentities.uuid(
                    com.otectus.arsnspells.contract.AnsModifierIds.ARS_GEAR_MAX_MANA);
                instance.removeModifier(ansId);
                instance.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(ansId,
                    "Ars Gear Max Mana", ARS_HELMET, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
                equal(helper, 350, instance.getValue(), "fixture: the 3.3.5/3.3.6 ceiling");
                com.otectus.arsnspells.bridge.BridgeManager.getBridge().setMana(player, 350);

                player.doTick();
                equal(helper, ARS_PROGRESSION + ARS_HELMET, instance.getValue(),
                    "one ordinary player tick restores the maximum");
                equal(helper, GLYPHS, ars(player).getGlyphBonus(), "progression untouched");
                equal(helper, 350, pool(player), "the balance itself is not invented");
                float previous = pool(player);
                for (int i = 0; i < 400 && pool(player) < ARS_PROGRESSION + ARS_HELMET; i++) {
                    regen(player);
                    helper.assertTrue(pool(player) >= previous, "regeneration lowered the pool");
                    previous = pool(player);
                }
                equal(helper, ARS_PROGRESSION + ARS_HELMET, pool(player), "regeneration refills to the restored maximum");
            });
        }

        /** Iron's and Ars casts debit exactly their price from a balance above 350, and regeneration tops up. */
        static void castingAndRegen(GameTestHelper helper) {
            inIssPrimary(1.0, () -> {
                var player = player(helper, "casting");
                player.setHealth(10);
                arsProgression(player);
                settle(player);
                double expected = ARS_PROGRESSION + ARS_HELMET;
                assertCapacity(helper, player, expected, "before casting");
                var bridge = com.otectus.arsnspells.bridge.BridgeManager.getBridge();
                bridge.setMana(player, 700);

                var spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell("irons_spellbooks:heal");
                int price = spell.getManaCost(1);
                var book = new net.minecraft.world.item.ItemStack(CrossCastGameTests.findIronsSpellBook());
                var container = io.redspace.ironsspellbooks.api.spells.ISpellContainer.getOrCreate(book).mutableCopy();
                for (int i = 0; i < container.getMaxSpellCount(); i++) container.removeSpellAtIndex(i);
                container.setMaxSpellCount(1);
                helper.assertTrue(container.addSpellAtIndex(spell, 1, 0, false), "could not prepare a native heal book");
                io.redspace.ironsspellbooks.api.spells.ISpellContainer.set(book, container.toImmutable());
                helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(player, book, 0), "native heal admission");
                drainWindup(player);
                helper.assertTrue(player.getHealth() > 10, "the native heal resolved");
                equal(helper, 700 - price, pool(player), "an Iron's cast above the old 350 ceiling pays exactly its price");

                player.setHealth(10);
                var arsHeal = new com.hollingsworth.arsnouveau.api.spell.Spell(
                    com.hollingsworth.arsnouveau.common.spell.method.MethodSelf.INSTANCE,
                    com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal.INSTANCE);
                float beforeArs = pool(player);
                var resolver = new com.hollingsworth.arsnouveau.api.spell.SpellResolver(
                    com.hollingsworth.arsnouveau.api.spell.SpellContext.fromEntity(arsHeal, player,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK)));
                helper.assertTrue(resolver.onCast(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK),
                    player.level()), "the Ars cast was refused");
                helper.assertTrue(player.getHealth() > 10, "the Ars heal resolved");
                equal(helper, beforeArs - arsHeal.getCost(), pool(player), "an Ars cast pays its cost from the same pool");
                helper.assertTrue(pool(player) > 350, "neither cast clamped the pool to the gear-only ceiling");

                bridge.setMana(player, 100);
                float previous = pool(player);
                for (int i = 0; i < 400 && pool(player) < expected; i++) {
                    regen(player);
                    helper.assertTrue(pool(player) >= previous && pool(player) <= expected + 1.0e-3,
                        "regeneration step " + i + " left the range: " + pool(player));
                    previous = pool(player);
                }
                equal(helper, expected, pool(player), "regeneration fills to the restored maximum");
            });
        }

        private static void drainWindup(net.minecraft.server.level.ServerPlayer player) {
            try {
                var manager = io.redspace.ironsspellbooks.api.magic.MagicHelper.MAGIC_MANAGER;
                var tick = manager.getClass().getDeclaredMethod("lambda$tick$0", boolean.class,
                    net.minecraft.world.entity.player.Player.class);
                tick.setAccessible(true);
                for (int guard = 0; data(player).isCasting() && guard < 500; guard++) tick.invoke(manager, false, player);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Iron's cast ticker could not be driven", error);
            }
            if (data(player).isCasting()) throw new IllegalStateException("the native cast did not finish");
        }

        /** The other modes are untouched: ars_primary and hybrid keep the shared ceiling, separate and disabled none. */
        static void otherModes(GameTestHelper helper) {
            double arsMax = ARS_PROGRESSION + ARS_HELMET;
            String previous = com.otectus.arsnspells.config.AnsConfig.MANA_UNIFICATION_MODE.get();
            // Mode to mode with a single restore: every config write is a file save.
            try {
                for (String mode : new String[] {"ars_primary", "hybrid", "separate", "disabled"}) {
                    TestConfig.set(com.otectus.arsnspells.config.AnsConfig.MANA_UNIFICATION_MODE, mode);
                    com.otectus.arsnspells.bridge.BridgeManager.refreshMode();
                    helper.assertTrue(mode.equals(com.otectus.arsnspells.bridge.BridgeManager.getCurrentMode().getConfigName()),
                        "could not enter " + mode);
                    var player = player(helper, "mode_" + mode);
                    arsProgression(player);
                    settle(player);
                    double expected = mode.equals("ars_primary") || mode.equals("hybrid") ? arsMax : 100.0;
                    equal(helper, expected, ironsMax(player).getValue(), mode + ": Iron's max_mana");
                }
            } finally {
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.MANA_UNIFICATION_MODE, previous);
                com.otectus.arsnspells.bridge.BridgeManager.refreshMode();
            }
        }

        /** The corrected ceiling is queued for the vanilla attribute packet that updates Iron's HUD. */
        static void clientSync(GameTestHelper helper) {
            inIssPrimary(1.0, () -> {
                var player = player(helper, "client_sync");
                helper.assertTrue(ironsMaxAttribute().isClientSyncable(), "Iron's max_mana must be a client-synced attribute");
                player.getAttributes().getDirtyAttributes().clear();
                arsProgression(player);
                settle(player);
                var dirty = player.getAttributes().getDirtyAttributes();
                helper.assertTrue(dirty.contains(ironsMax(player)),
                    "the restored ceiling must be queued for the client's attribute update");
                equal(helper, ARS_PROGRESSION + ARS_HELMET, ironsMax(player).getValue(), "the queued value");
                dirty.clear();
                for (int i = 0; i < 3; i++) settle(player);
                helper.assertTrue(!player.getAttributes().getDirtyAttributes().contains(ironsMax(player)),
                    "an unchanged ceiling must not be re-sent every reconciliation");
            });
        }
    }
}
