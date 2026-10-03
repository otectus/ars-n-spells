package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 3.3.5: the native cooldown an Ars spell bound into an Iron's spellbook starts after a
 * successful cast from Iron's spell wheel. Every assertion reads Iron's own cooldown store for
 * the proxy spell id, which is what the wheel displays and what gates the next cast.
 *
 * <p>Iron's-loaded profile only; each test self-skips without Iron's.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class InscribedCooldownGameTests {
    private InscribedCooldownGameTests() {}

    @GameTest(template = "platform", batch = "ans_inscribed_cooldown")
    public static void ironsLoaded_successfulProxyCastStartsConfiguredNativeCooldown(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withCooldown(40, () -> Loaded.successStartsCooldown(helper));
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_inscribed_cooldown")
    public static void ironsLoaded_failedOrUnpaidProxyCastStartsNoCooldown(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withCooldown(40, () -> Loaded.failureStartsNothing(helper));
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_inscribed_cooldown")
    public static void ironsLoaded_zeroCooldownReproducesPreviousBehaviour(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withCooldown(0, () -> Loaded.zeroMeansNone(helper));
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_inscribed_cooldown")
    public static void ironsLoaded_cooldownReductionAttributeShortensProxyCooldown(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withCooldown(100, () -> Loaded.reductionApplies(helper));
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_inscribed_cooldown")
    public static void ironsLoaded_reusedProxySlotSharesCooldownAcrossBooks(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withCooldown(40, () -> Loaded.sharedAcrossBooks(helper));
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_inscribed_cooldown")
    public static void ironsLoaded_categoryCooldownAndProxyCooldownBothGate(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withCooldown(40, () -> Loaded.categoryIntersection(helper));
        helper.succeed();
    }

    private static void withCooldown(int ticks, Runnable body) {
        int previous = AnsConfig.INSCRIBED_ARS_DEFAULT_COOLDOWN_TICKS.get();
        String mode = AnsConfig.MANA_UNIFICATION_MODE.get();
        boolean cooldowns = AnsConfig.ENABLE_COOLDOWN_SYSTEM.get(), unified = AnsConfig.ENABLE_UNIFIED_COOLDOWNS.get(),
            crossMod = AnsConfig.ENABLE_CROSS_MOD_COOLDOWNS.get();
        try {
            TestConfig.set(AnsConfig.INSCRIBED_ARS_DEFAULT_COOLDOWN_TICKS, ticks);
            TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, "iss_primary");
            BridgeManager.refreshMode();
            body.run();
        } finally {
            TestConfig.set(AnsConfig.INSCRIBED_ARS_DEFAULT_COOLDOWN_TICKS, previous);
            TestConfig.set(AnsConfig.ENABLE_COOLDOWN_SYSTEM, cooldowns);
            TestConfig.set(AnsConfig.ENABLE_UNIFIED_COOLDOWNS, unified);
            TestConfig.set(AnsConfig.ENABLE_CROSS_MOD_COOLDOWNS, crossMod);
            TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, mode);
            BridgeManager.refreshMode();
        }
    }

    /** Iron's classes resolve only inside this holder, so the Iron's-absent profile never loads them. */
    private static final class Loaded {
        private static io.redspace.ironsspellbooks.api.magic.MagicData data(ServerPlayer player) {
            return io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player);
        }

        private static io.redspace.ironsspellbooks.api.spells.AbstractSpell proxy(int poolId) {
            return com.otectus.arsnspells.spell.irons.ArsCrossProxyRegistry.get(poolId);
        }

        /** A book whose wheel slot for pool 1 casts a real Self+Heal. */
        private static ItemStack healBook(GameTestHelper helper, String name) {
            ItemStack book = IronsTableDriver.freshBook();
            var bound = IronsBookBindingUtil.appendArsSpellToBook(book,
                CrossCastingHandler.encodeArsSpell(new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE)), name, "water", "heart", -1);
            helper.assertTrue(bound.wasAdded(), "bind must add, got " + bound);
            helper.assertTrue(CrossModSpellComponents.findEntryByProxyPoolId(book, 1).isPresent(), "bind must use pool 1");
            return book;
        }

        /** A hurt survival caster with both native pools funded and no cooldowns. */
        private static ServerPlayer caster(GameTestHelper helper, String scenario) {
            ServerPlayer player = IronsInscriptionTableGameTests.scenarioPlayer(helper, "icd_" + scenario);
            player.setGameMode(GameType.SURVIVAL);
            player.setHealth(10);
            data(player).setServerPlayer(player);
            data(player).resetCastingState();
            data(player).getPlayerCooldowns().clearCooldowns();
            player.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA).setBaseValue(10000);
            com.otectus.arsnspells.bridge.NativeManaAccess.with(player, ResourceUnit.ARS_MANA, () -> {
                var mana = com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.getMana(player);
                mana.setMaxMana(10000); mana.setMana(9000); return null;
            });
            BridgeManager.getNativeIronsBridge().setMana(player, 9000);
            return player;
        }

        private static int remaining(ServerPlayer player, io.redspace.ironsspellbooks.api.spells.AbstractSpell spell) {
            var instance = data(player).getPlayerCooldowns().getSpellCooldowns().get(spell.getSpellId());
            return instance == null ? 0 : instance.getSpellCooldown();
        }

        static void successStartsCooldown(GameTestHelper helper) {
            ServerPlayer player = caster(helper, "success");
            var proxy = proxy(1);
            helper.assertTrue(proxy.getSpellCooldown() == 40, "proxy base cooldown must read the configured default, got " + proxy.getSpellCooldown());
            IronsProxyCastDriver.castViaEquippedSpellbook(player, healBook(helper, "Cooldown Heal"), 1);
            helper.assertTrue(player.getHealth() > 10, "the delegated Ars heal must resolve");
            int expected = io.redspace.ironsspellbooks.capabilities.magic.MagicManager.getEffectiveSpellCooldown(
                proxy, player, io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK);
            helper.assertTrue(expected > 0, "effective cooldown must be positive at the default reduction");
            helper.assertTrue(remaining(player, proxy) == expected, "native proxy cooldown must be Iron's effective value "
                + expected + ", got " + remaining(player, proxy));
            helper.assertTrue(!proxy.canBeCastedBy(1, io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK, data(player), player).isSuccess(),
                "Iron's must refuse the proxy slot while it cools down");
            var heal = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell("irons_spellbooks:heal");
            helper.assertTrue(!data(player).getPlayerCooldowns().isOnCooldown(heal), "an ordinary Iron's spell must not inherit the proxy cooldown");
        }

        static void failureStartsNothing(GameTestHelper helper) {
            var proxy = proxy(1);
            ServerPlayer missing = caster(helper, "missing");
            IronsProxyCastDriver.castViaEquippedSpellbook(missing, IronsTableDriver.freshBook(), 1);
            helper.assertTrue(missing.getHealth() == 10, "a payload-less slot must not resolve");
            helper.assertTrue(!data(missing).getPlayerCooldowns().isOnCooldown(proxy), "a missing payload must start no cooldown");
            ServerPlayer broke = caster(helper, "unpaid");
            BridgeManager.getNativeIronsBridge().setMana(broke, 0);
            IronsProxyCastDriver.castViaEquippedSpellbook(broke, healBook(helper, "Unpaid Heal"), 1);
            helper.assertTrue(broke.getHealth() == 10, "an unpaid delegated cast must not heal");
            helper.assertTrue(!data(broke).getPlayerCooldowns().isOnCooldown(proxy), "an unpaid delegated cast must start no cooldown");
        }

        static void zeroMeansNone(GameTestHelper helper) {
            ServerPlayer player = caster(helper, "zero");
            var proxy = proxy(1);
            helper.assertTrue(proxy.getSpellCooldown() == 0, "0 must disable the proxy default");
            IronsProxyCastDriver.castViaEquippedSpellbook(player, healBook(helper, "Zero Heal"), 1);
            helper.assertTrue(player.getHealth() > 10, "the delegated Ars heal must resolve");
            // Iron's natively records a zero-length entry that its next cooldown tick clears;
            // 3.3.3 and 3.3.4 did exactly the same, and a FakePlayer is not ticked by Iron's.
            helper.assertTrue(remaining(player, proxy) == 0, "0 must record no cooldown length, got " + remaining(player, proxy));
            data(player).getPlayerCooldowns().tick(1);
            helper.assertTrue(!data(player).getPlayerCooldowns().isOnCooldown(proxy), "0 must leave the proxy slot ready, as before 3.3.5");
        }

        static void reductionApplies(GameTestHelper helper) {
            ServerPlayer player = caster(helper, "reduction");
            var proxy = proxy(1);
            var attribute = player.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.COOLDOWN_REDUCTION);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("ars_n_spells", "gametest_cooldown_reduction");
            if (attribute.getModifier(id) != null) attribute.removeModifier(id);
            int unreduced = io.redspace.ironsspellbooks.capabilities.magic.MagicManager.getEffectiveSpellCooldown(
                proxy, player, io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK);
            attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                id, 0.5, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            try {
                IronsProxyCastDriver.castViaEquippedSpellbook(player, healBook(helper, "Quick Heal"), 1);
                int reduced = remaining(player, proxy);
                helper.assertTrue(reduced > 0 && reduced < unreduced, "cooldown reduction must shorten the proxy cooldown: "
                    + unreduced + " -> " + reduced);
            } finally {
                attribute.removeModifier(id);
            }
        }

        static void sharedAcrossBooks(GameTestHelper helper) {
            ServerPlayer player = caster(helper, "shared");
            var proxy = proxy(1);
            ItemStack first = healBook(helper, "First Heal");
            ItemStack second = healBook(helper, "Second Heal");
            IronsProxyCastDriver.castViaEquippedSpellbook(player, first, 1);
            helper.assertTrue(data(player).getPlayerCooldowns().isOnCooldown(proxy), "the first book's cast starts the slot cooldown");
            IronsProxyCastDriver.equipSpellbook(player, second);
            helper.assertTrue(!proxy.canBeCastedBy(1, io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK, data(player), player).isSuccess(),
                "a second book reusing pool 1 shares the documented slot cooldown");
        }

        static void categoryIntersection(GameTestHelper helper) {
            TestConfig.set(AnsConfig.ENABLE_COOLDOWN_SYSTEM, true);
            TestConfig.set(AnsConfig.ENABLE_UNIFIED_COOLDOWNS, true);
            TestConfig.set(AnsConfig.ENABLE_CROSS_MOD_COOLDOWNS, true);
            ServerPlayer player = caster(helper, "category");
            var proxy = proxy(1);
            IronsProxyCastDriver.castViaEquippedSpellbook(player, healBook(helper, "Category Heal"), 1);
            helper.assertTrue(player.getHealth() > 10, "the delegated Ars heal must resolve");
            var category = com.otectus.arsnspells.util.SpellAnalysis.analyze(new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE)).category();
            helper.assertTrue(com.otectus.arsnspells.cooldown.UnifiedCooldownManager.isOnCooldown(player, category),
                "the enabled category cooldown still follows the delegated Ars cast");
            helper.assertTrue(data(player).getPlayerCooldowns().isOnCooldown(proxy),
                "the per-proxy native cooldown is not skipped when category cooldowns are on");
            helper.assertTrue(data(player).getPlayerCooldowns().getSpellCooldowns().size() == 1,
                "exactly one native timer: the proxy slot");
        }
    }
}
