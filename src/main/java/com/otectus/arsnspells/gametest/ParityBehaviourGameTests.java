package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.IronsCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Behaviour that the Forge 1.20.1 and NeoForge 1.21.1 builds must share, asserted through
 * observable effects on real players. The NeoForge build carries a class of the same name with
 * the same tests; {@code tools/verify_loader_parity.py} fails if the two sets drift apart.
 *
 * <ul>
 *   <li>Source Jar regen synergy reaches the shared pool through the event bus (it was never
 *       registered on NeoForge before 3.3.5).</li>
 *   <li>ISS_PRIMARY adds the Ars gear bonus times the conversion rate to Iron's max mana; it does
 *       not raise Iron's max to Ars's real max (the NeoForge build did before 3.3.5).</li>
 *   <li>HYBRID keeps the shared-pool ceiling with {@code respect_armor_bonuses} off.</li>
 *   <li>An Ars Mana Regen potion is mirrored onto Iron's regen in ISS_PRIMARY, once, and taken
 *       off when the mode changes.</li>
 *   <li>A curio in {@code #ars_n_spells:curio_spell_discount} discounts Ars and Iron's casts by
 *       the same arithmetic (new on Forge in 3.3.5).</li>
 * </ul>
 *
 * <p>Iron's-only: every scenario reads or writes Iron's attributes, so each self-skips without
 * Iron's. The Iron's-typed bodies live in {@link Loaded} so the GameTest scanner never links
 * Iron's classes on the Iron's-absent profile.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class ParityBehaviourGameTests {

    private ParityBehaviourGameTests() {}

    @GameTest(template = "platform", batch = "ans_parity_behaviour")
    public static void ironsLoaded_sourceJarNearby_regeneratesTheSharedPool(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.sourceJar(helper);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_parity_behaviour")
    public static void ironsLoaded_issPrimary_arsGearAddsRateScaledBonus(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.issPrimaryGear(helper);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_parity_behaviour")
    public static void ironsLoaded_hybridWithoutArmorBonuses_keepsTheCeiling(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.hybridCeiling(helper);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_parity_behaviour")
    public static void ironsLoaded_issPrimary_arsManaRegenPotionIsMirroredOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.potion(helper);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_parity_behaviour")
    public static void ironsLoaded_taggedCurioDiscountsArsAndIronsCasts(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.curioDiscount(helper);
        helper.succeed();
    }

    private static final class Loaded {
        private static final String GEAR_NAME = "ans_parity_gear";
        private static final double GEAR_MAX_MANA = 50.0;
        private static final float EPSILON = 0.01f;

        private static net.minecraft.server.level.ServerPlayer player(GameTestHelper helper, String scenario) {
            var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(
                    ("ans_parity/" + scenario).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    scenario.length() > 16 ? scenario.substring(0, 16) : scenario));
            com.otectus.arsnspells.bridge.AnsFeatureCleanup.removeAll(player);
            com.otectus.arsnspells.util.ManaUtil.getNativeMana(player).ifPresent(cap -> {
                cap.setGlyphBonus(0);
                cap.setBookTier(0);
            });
            var data = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player);
            data.setServerPlayer(player);
            player.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get()).setBaseValue(100);
            data.setMana(0);
            player.removeAllEffects();
            return player;
        }

        /** Run {@code body} in {@code mode} with {@code rate}, restoring both afterwards. */
        private static void inMode(GameTestHelper helper, String mode, double rate, Runnable body) {
            String previousMode = com.otectus.arsnspells.config.AnsConfig.MANA_UNIFICATION_MODE.get();
            double previousRate = com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get();
            boolean previousRespect = com.otectus.arsnspells.config.AnsConfig.respectArmorBonuses.get();
            try {
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.MANA_UNIFICATION_MODE, mode);
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_ARS_TO_IRON, rate);
                com.otectus.arsnspells.bridge.BridgeManager.refreshMode();
                helper.assertTrue(mode.equals(com.otectus.arsnspells.bridge.BridgeManager.getCurrentMode().getConfigName()),
                    "could not enter " + mode + "; got " + com.otectus.arsnspells.bridge.BridgeManager.getCurrentMode());
                body.run();
            } finally {
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.MANA_UNIFICATION_MODE, previousMode);
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_ARS_TO_IRON, previousRate);
                TestConfig.set(com.otectus.arsnspells.config.AnsConfig.respectArmorBonuses, previousRespect);
                com.otectus.arsnspells.bridge.BridgeManager.refreshMode();
            }
        }

        // ---- Source Jar synergy ----

        static void sourceJar(GameTestHelper helper) {
            var jarBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(
                new net.minecraft.resources.ResourceLocation("ars_nouveau", "source_jar"));
            helper.assertTrue(jarBlock.isPresent(), "ars_nouveau:source_jar must be registered");
            TestConfig.set(com.otectus.arsnspells.config.AnsConfig.ENABLE_SOURCE_JAR_SYNERGY, true);
            inMode(helper, "iss_primary", 1.0, () -> {
                helper.setBlock(new net.minecraft.core.BlockPos(1, 2, 1), jarBlock.get());
                var near = player(helper, "jar_near");
                near.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2.0, 2.5)));
                // A control far from any jar, so whatever else a player tick does to the pool
                // cancels out of the comparison.
                var far = player(helper, "jar_far");
                far.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2.0, 2.5)).add(4096, 0, 4096));
                var bridge = com.otectus.arsnspells.bridge.BridgeManager.getBridge();
                bridge.setMana(near, 10);
                bridge.setMana(far, 10);
                float nearBefore = bridge.getMana(near);
                float farBefore = bridge.getMana(far);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                    net.minecraftforge.event.TickEvent.Phase.END, near));
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                    net.minecraftforge.event.TickEvent.Phase.END, far));
                double expected = com.otectus.arsnspells.util.SourceSynergyPolicy.income(
                    com.otectus.arsnspells.config.AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get()
                        * com.otectus.arsnspells.config.AnsConfig.SOURCE_JAR_SYNERGY_MULTIPLIER.get(), 1);
                float gained = (bridge.getMana(near) - nearBefore) - (bridge.getMana(far) - farBefore);
                helper.assertTrue(expected > 0, "test setup failed: the synergy income must be positive");
                helper.assertTrue(Math.abs(gained - expected) < EPSILON,
                    "a player tick next to a Source Jar must add " + expected + " to the shared pool, added "
                        + gained + " (0 means the synergy handler is not on the event bus)");
            });
        }

        // ---- Ars gear onto Iron's ----

        /** Adds the fixture's Ars max-mana bonus to a helmet carrying {@link #GEAR_NAME}. */
        private static final java.util.function.Consumer<net.minecraftforge.event.ItemAttributeModifierEvent> GEAR =
            event -> {
                var stack = event.getItemStack();
                if (event.getSlotType() == net.minecraft.world.entity.EquipmentSlot.HEAD
                        && stack.hasCustomHoverName() && GEAR_NAME.equals(stack.getHoverName().getString())) {
                    event.addModifier(com.hollingsworth.arsnouveau.api.perk.PerkAttributes.MAX_MANA.get(),
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            java.util.UUID.nameUUIDFromBytes("ans_parity_fixture:gear_max_mana"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)), "ans_parity_fixture",
                            GEAR_MAX_MANA, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
                }
            };

        private static void wearGear(net.minecraft.server.level.ServerPlayer player) {
            var helmet = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET);
            helmet.setHoverName(net.minecraft.network.chat.Component.literal(GEAR_NAME));
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helmet);
        }

        private static double gearModifier(net.minecraft.server.level.ServerPlayer player) {
            var instance = player.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
            var modifier = instance.getModifier(com.otectus.arsnspells.bridge.AnsModifierIdentities.uuid(
                com.otectus.arsnspells.contract.AnsModifierIds.ARS_GEAR_MAX_MANA));
            return modifier == null ? 0.0 : modifier.getAmount();
        }

        static void issPrimaryGear(GameTestHelper helper) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(GEAR);
            var player = player(helper, "gear_iss");
            try {
                inMode(helper, "iss_primary", 2.0, () -> {
                    // A large Ars pool: the pre-3.3.5 NeoForge ceiling would have lifted Iron's max
                    // to it, which this bonus is not.
                    com.otectus.arsnspells.util.ManaUtil.getNativeMana(player).ifPresent(cap -> cap.setGlyphBonus(400));
                    wearGear(player);
                    com.otectus.arsnspells.equipment.EquipmentIntegration.clearCache(player);
                    com.otectus.arsnspells.events.EquipmentHandler.recomputeContributions(player);
                    double modifier = gearModifier(player);
                    helper.assertTrue(Math.abs(modifier - GEAR_MAX_MANA * 2.0) < EPSILON,
                        "ISS_PRIMARY must add the Ars gear bonus times the conversion rate ("
                            + (GEAR_MAX_MANA * 2.0) + ") to Iron's max mana, added " + modifier);
                });
            } finally {
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
                com.otectus.arsnspells.bridge.AnsFeatureCleanup.removeAll(player);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(GEAR);
            }
        }

        static void hybridCeiling(GameTestHelper helper) {
            var player = player(helper, "hybrid_ceiling");
            try {
                inMode(helper, "hybrid", 1.0, () -> {
                    TestConfig.set(com.otectus.arsnspells.config.AnsConfig.respectArmorBonuses, false);
                    com.otectus.arsnspells.util.ManaUtil.getNativeMana(player).ifPresent(cap -> cap.setGlyphBonus(400));
                    com.otectus.arsnspells.equipment.EquipmentIntegration.clearCache(player);
                    com.otectus.arsnspells.events.EquipmentHandler.recomputeContributions(player);
                    float arsMax = com.otectus.arsnspells.equipment.EquipmentIntegration.arsRealMaxMana(player);
                    double ironsMax = player.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
                    helper.assertTrue(arsMax > 100, "test setup failed: Ars's max must exceed Iron's own 100, got " + arsMax);
                    helper.assertTrue(ironsMax + EPSILON >= arsMax, "HYBRID must keep Iron's max at or above Ars's real max "
                        + arsMax + " with respect_armor_bonuses off, got " + ironsMax
                        + "; a lower ceiling deletes mana on the next write");
                });
            } finally {
                com.otectus.arsnspells.bridge.AnsFeatureCleanup.removeAll(player);
            }
        }

        // ---- Ars potions onto Iron's ----

        static void potion(GameTestHelper helper) {
            var effect = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getOptional(
                new net.minecraft.resources.ResourceLocation("ars_nouveau", "mana_regen"));
            helper.assertTrue(effect.isPresent(), "ars_nouveau:mana_regen must be registered");
            var player = player(helper, "potion_iss");
            var id = com.otectus.arsnspells.bridge.AnsModifierIdentities.uuid(
                com.otectus.arsnspells.contract.AnsModifierIds.ARS_POTION_MANA_REGEN);
            var regen = player.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MANA_REGEN.get());
            try {
                inMode(helper, "iss_primary", 1.0, () -> {
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect.get(), 200, 0));
                    com.otectus.arsnspells.equipment.PotionContributions.reconcile(player);
                    com.otectus.arsnspells.equipment.PotionContributions.reconcile(player);
                    var modifier = regen.getModifier(id);
                    double expected = com.otectus.arsnspells.bridge.ManaRegenBridge.convertArsToIrons(0.5, player);
                    helper.assertTrue(modifier != null && Math.abs(modifier.getAmount() - expected) < 1.0e-6,
                        "ISS_PRIMARY must mirror a level-1 Ars Mana Regen potion onto Iron's regen as "
                            + expected + ", once; got " + (modifier == null ? "nothing" : modifier.getAmount()));
                });
                inMode(helper, "hybrid", 1.0, () -> {
                    com.otectus.arsnspells.equipment.PotionContributions.reconcile(player);
                    helper.assertTrue(regen.getModifier(id) == null,
                        "leaving ISS_PRIMARY must take the mirrored potion modifier off");
                });
            } finally {
                player.removeAllEffects();
                com.otectus.arsnspells.bridge.AnsFeatureCleanup.removeAll(player);
            }
        }

        // ---- Tagged curio discount ----

        static void curioDiscount(GameTestHelper helper) {
            var tagged = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getTag(com.otectus.arsnspells.events.CurioDiscountHandler.CURIO_SPELL_DISCOUNT_TAG)
                .flatMap(set -> set.stream().findFirst());
            helper.assertTrue(tagged.isPresent(), "#ars_n_spells:curio_spell_discount resolves to no item with Iron's "
                + "loaded; its shipped #irons_spellbooks:school_focus entry must populate it");
            var player = player(helper, "curio_discount");
            var inventory = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve();
            helper.assertTrue(inventory.isPresent() && !inventory.get().getCurios().isEmpty(),
                "the test player must have Curios slots");
            var handler = inventory.get().getCurios().values().iterator().next();
            var stacks = handler.getStacks();
            var previous = stacks.getStackInSlot(0);
            stacks.setStackInSlot(0, new net.minecraft.world.item.ItemStack(tagged.get()));
            try {
                double factor = Math.max(1.0 - com.otectus.arsnspells.config.AnsConfig.VIRTUE_RING_DISCOUNT.get(),
                    1.0 - com.otectus.arsnspells.config.AnsConfig.MAX_TOTAL_CURIO_DISCOUNT.get());
                int expected = (int) Math.max(1, Math.round(100 * factor));
                helper.assertTrue(expected < 100, "test setup failed: the configured discount must lower a 100 cost");

                var spell = new com.hollingsworth.arsnouveau.api.spell.Spell(
                    com.hollingsworth.arsnouveau.common.spell.method.MethodSelf.INSTANCE,
                    com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal.INSTANCE);
                var arsEvent = new com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent(
                    com.hollingsworth.arsnouveau.api.spell.SpellContext.fromEntity(spell, player,
                        net.minecraft.world.item.ItemStack.EMPTY), 100);
                com.otectus.arsnspells.events.CurioDiscountHandler.onSpellCostCalc(arsEvent);
                helper.assertTrue(arsEvent.currentCost == expected,
                    "one tagged curio must discount a 100-mana Ars cast to " + expected + ", got " + arsEvent.currentCost);

                var ironsEvent = new io.redspace.ironsspellbooks.api.events.SpellOnCastEvent(player,
                    "irons_spellbooks:fireball", 1, 100, io.redspace.ironsspellbooks.api.registry.SchoolRegistry.FIRE.get(),
                    io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK);
                new com.otectus.arsnspells.compat.curios.IronsCurioDiscountHandler().onIronsSpellCast(ironsEvent);
                helper.assertTrue(ironsEvent.getManaCost() == expected,
                    "one tagged curio must discount a 100-mana Iron's cast to " + expected + ", got " + ironsEvent.getManaCost());
            } finally {
                stacks.setStackInSlot(0, previous);
            }
        }
    }
}
