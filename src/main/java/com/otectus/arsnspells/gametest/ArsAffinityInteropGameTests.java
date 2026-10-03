package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.event.SpellDamageEvent;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellSchools;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.NativeManaAccess;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 3.3.5: Ars Affinity interoperability, measured against the published 1.1.1 jar
 * ({@code -PwithArsAffinity}). ANS registers no Affinity integration, so these tests establish
 * that the two mods already interoperate: Affinity observes a delegated Ars cast through its own
 * resolver mixin exactly as often as a native one, never counts an Iron's spell as glyph
 * progress, and its Mana Tap perk restores whichever pool ANS routes Ars mana writes to.
 *
 * <p>Affinity is reached only by reflection: the jar is never on the compile classpath, and
 * every test self-skips when {@code ars_affinity} is absent.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class ArsAffinityInteropGameTests {
    private ArsAffinityInteropGameTests() {}

    static final String AFFINITY = "ars_affinity";
    private static final String[] MODES = {"disabled", "separate", "ars_primary", "iss_primary", "hybrid"};

    @GameTest(template = "platform", batch = "ans_ars_affinity")
    public static void affinityLoaded_nativeArsCastProgressesAffinity(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, AFFINITY)) return;
        ServerPlayer player = realPlayer(helper);
        try {
            double before = Probe.totalProgress(player);
            castNativeHeal(player);
            helper.assertTrue(player.getHealth() > 10, "the native Ars heal must resolve");
            double after = Probe.totalProgress(player);
            helper.assertTrue(after > before, "Ars Affinity must record progress for a native Ars cast: " + before + " -> " + after
                + "; " + Probe.describe(player));
        } finally {
            release(player);
        }
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_ars_affinity")
    public static void affinityLoaded_proxyArsCastProgressesExactlyOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, AFFINITY) || OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withMode("iss_primary", () -> {
            ServerPlayer nativeCaster = realPlayer(helper);
            ServerPlayer proxyCaster = realPlayer(helper);
            try {
                double nativeBefore = Probe.totalProgress(nativeCaster);
                castNativeHeal(nativeCaster);
                double nativeDelta = Probe.totalProgress(nativeCaster) - nativeBefore;

                ItemStack book = IronsTableDriver.freshBook();
                var bound = IronsBookBindingUtil.appendArsSpellToBook(book,
                    CrossCastingHandler.encodeArsSpell(heal()), "Affinity Heal", "water", "heart", -1);
                helper.assertTrue(bound.wasAdded(), "bind must add, got " + bound);
                double proxyBefore = Probe.totalProgress(proxyCaster);
                IronsProxyCastDriver.castViaEquippedSpellbook(proxyCaster, book, 1);
                helper.assertTrue(proxyCaster.getHealth() > 10, "the delegated Ars heal must resolve");
                double proxyDelta = Probe.totalProgress(proxyCaster) - proxyBefore;

                helper.assertTrue(nativeDelta > 0, "the native control must progress Affinity: " + Probe.describe(nativeCaster));
                helper.assertTrue(Math.abs(proxyDelta - nativeDelta) < 1e-6,
                    "a spell cast from Iron's wheel must progress Affinity exactly as the same native cast: native "
                        + nativeDelta + ", proxy " + proxyDelta);
            } finally {
                release(nativeCaster);
                release(proxyCaster);
            }
        });
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_ars_affinity")
    public static void affinityLoaded_nativeIronsSpellAddsNoGlyphProgress(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, AFFINITY) || OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withMode("iss_primary", () -> {
            ServerPlayer player = realPlayer(helper);
            try {
                double before = Probe.totalProgress(player);
                Loaded.castIronsHeal(player);
                helper.assertTrue(player.getHealth() > 10, "the native Iron's heal must resolve");
                helper.assertTrue(Math.abs(Probe.totalProgress(player) - before) < 1e-9,
                    "an Iron's spell has no Ars glyphs and must not invent Affinity progress");
                // Positive control on the same caster: the probe can see progress at all.
                castNativeHeal(player);
                helper.assertTrue(Probe.totalProgress(player) > before, "control: an Ars cast by the same caster progresses Affinity");
            } finally {
                release(player);
            }
        });
        helper.succeed();
    }

    /**
     * Mana Tap restores mana through {@code IManaCap}. ANS routes those accessors per mode, so the
     * restore must land, exactly once, in the pool that mode makes authoritative for Ars mana.
     */
    @GameTest(template = "platform", batch = "ans_ars_affinity")
    public static void affinityLoaded_manaTapRestoresTheRoutedPoolInEveryMode(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, AFFINITY)) return;
        for (String mode : IronsCompat.isLoaded() ? MODES : new String[]{"disabled"}) {
            withMode(mode, () -> {
                ServerPlayer player = caster(helper, "aff_tap_" + mode);
                Probe.allocate(helper, player, "manipulation_manipulation_power_1");
                Probe.allocate(helper, player, "manipulation_mana_tap_1");
                float damage = 20;
                double restore = Probe.manaTapAmount(player) * damage;
                helper.assertTrue(restore > 0, "the allocated Mana Tap perk must restore something");
                boolean ironsPays = mode.equals("iss_primary") || mode.equals("hybrid");
                ResourceUnit payer = ironsPays ? ResourceUnit.IRONS_MANA : ResourceUnit.ARS_MANA;
                double payerBefore = BridgeManager.getNativeBridge(payer).transactionMana(player);
                double dormantBefore = IronsCompat.isLoaded() && !mode.equals("disabled") && !mode.equals("separate")
                    ? BridgeManager.getNativeBridge(ironsPays ? ResourceUnit.ARS_MANA : ResourceUnit.IRONS_MANA).transactionMana(player)
                    : Double.NaN;
                SpellContext context = SpellContext.fromEntity(heal(), player, new ItemStack(Items.STICK));
                NeoForge.EVENT_BUS.post(new SpellDamageEvent.Post(player.damageSources().magic(), player, player, damage, context));
                double payerAfter = BridgeManager.getNativeBridge(payer).transactionMana(player);
                helper.assertTrue(Math.abs(payerAfter - payerBefore - restore) < 1e-3,
                    "Mana Tap in " + mode + " must restore " + restore + " to " + payer + " exactly once: "
                        + payerBefore + " -> " + payerAfter);
                if (!Double.isNaN(dormantBefore)) {
                    double dormantAfter = BridgeManager.getNativeBridge(ironsPays ? ResourceUnit.ARS_MANA : ResourceUnit.IRONS_MANA)
                        .transactionMana(player);
                    helper.assertTrue(Math.abs(dormantAfter - dormantBefore) < 1e-6,
                        "Mana Tap in " + mode + " must not move the dormant pool: " + dormantBefore + " -> " + dormantAfter);
                }
            });
        }
        helper.succeed();
    }

    private static Spell heal() {
        return new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE);
    }

    private static void castNativeHeal(ServerPlayer player) {
        ItemStack stick = new ItemStack(Items.STICK);
        new SpellResolver(SpellContext.fromEntity(heal(), player, stick)).onCast(stick, player.level());
    }

    /**
     * A plain server player that is not a FakePlayer. Ars wraps a FakePlayer in a plain
     * {@code LivingCaster}, never a {@code PlayerCaster}, and Affinity only tracks the latter,
     * so progress needs a player a real client would be. It is not placed in the player list
     * (Iron's login payloads refuse the GameTest mock connection); NeoForge's own no-op
     * FakePlayer network handler discards what casting sends to it.
     */
    private static ServerPlayer realPlayer(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ans_aff_probe"),
            net.minecraft.server.level.ClientInformation.createDefault());
        try {
            var handler = Class.forName("net.neoforged.neoforge.common.util.FakePlayer$FakePlayerNetHandler")
                .getDeclaredConstructor(net.minecraft.server.MinecraftServer.class, ServerPlayer.class);
            handler.setAccessible(true);
            player.connection = (net.minecraft.server.network.ServerGamePacketListenerImpl)
                handler.newInstance(level.getServer(), player);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("NeoForge FakePlayer network handler changed", error);
        }
        player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)));
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(10);
        NativeManaAccess.with(player, ResourceUnit.ARS_MANA, () -> {
            var mana = CapabilityRegistry.getMana(player);
            mana.setMaxMana(5000); mana.setMana(1000); return null;
        });
        if (IronsCompat.isLoaded()) Loaded.prepare(player);
        return player;
    }

    private static void release(ServerPlayer player) {
        player.discard();
    }

    /** A hurt survival caster with both native pools funded well below their ceilings. */
    private static ServerPlayer caster(GameTestHelper helper, String scenario) {
        ServerPlayer player = IronsInscriptionTableGameTests.scenarioPlayer(helper, scenario);
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(10);
        NativeManaAccess.with(player, ResourceUnit.ARS_MANA, () -> {
            var mana = CapabilityRegistry.getMana(player);
            mana.setMaxMana(5000); mana.setMana(1000); return null;
        });
        if (IronsCompat.isLoaded()) Loaded.prepare(player);
        return player;
    }

    private static void withMode(String mode, Runnable body) {
        String previous = AnsConfig.MANA_UNIFICATION_MODE.get();
        try {
            TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, mode);
            BridgeManager.refreshMode();
            body.run();
        } finally {
            TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, previous);
            BridgeManager.refreshMode();
        }
    }

    /** Iron's classes resolve only here, so an Affinity-without-Iron's profile never loads them. */
    private static final class Loaded {
        static void prepare(ServerPlayer player) {
            var data = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player);
            data.setServerPlayer(player);
            data.resetCastingState();
            data.getPlayerCooldowns().clearCooldowns();
            player.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA).setBaseValue(5000);
            BridgeManager.getNativeIronsBridge().setMana(player, 2000);
        }

        static void castIronsHeal(ServerPlayer player) {
            var spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell("irons_spellbooks:heal");
            var source = io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK;
            spell.attemptInitiateCast(ItemStack.EMPTY, 1, player.level(), player, source, false, "mainhand");
            spell.castSpell(player.level(), 1, player, source, false);
        }
    }

    /** Reflection into the published Ars Affinity 1.1.1 API; never compiled against. */
    private static final class Probe {
        private static Object data(Player player) {
            try {
                return Class.forName("com.github.ars_affinity.capability.PlayerAffinityDataHelper")
                    .getMethod("getPlayerAffinityData", Player.class).invoke(null, player);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Ars Affinity 1.1.1 player data API changed", error);
            }
        }

        /** Sum of Affinity's per-school progress: invariant to which school a glyph maps to. */
        static double totalProgress(Player player) {
            try {
                Object data = data(player);
                Map<?, ?> percentages = (Map<?, ?>) data.getClass().getMethod("getAllSchoolPercentages").invoke(data);
                double total = 0;
                for (Object value : percentages.values()) total += ((Number) value).doubleValue();
                return total;
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Ars Affinity 1.1.1 progress API changed", error);
            }
        }

        /** Affinity's own view of this player and of the fixture glyph, for failure messages. */
        static String describe(Player player) {
            try {
                Object data = data(player);
                Object percentages = data.getClass().getMethod("getAllSchoolPercentages").invoke(data);
                Object points = data.getClass().getMethod("getAllSchoolPoints").invoke(data);
                Object gain = Class.forName("com.github.ars_affinity.config.ArsAffinityConfig")
                    .getField("AFFINITY_GAIN_MULTIPLIER").get(null);
                Object gainValue = gain.getClass().getMethod("get").invoke(gain);
                return "percentages=" + percentages + " points=" + points + " healSchools=" + EffectHeal.INSTANCE.spellSchools
                    + " healCost=" + EffectHeal.INSTANCE.getCastingCost() + " gain=" + gainValue;
            } catch (ReflectiveOperationException | RuntimeException error) {
                return "affinity state unreadable: " + error;
            }
        }

        /** Allocate a perk node through Affinity's own rules, granting the one point it costs. */
        static void allocate(GameTestHelper helper, Player player, String nodeId) {
            try {
                Object node = Class.forName("com.github.ars_affinity.perk.PerkTreeManager")
                    .getMethod("getNode", String.class).invoke(null, nodeId);
                helper.assertTrue(node != null, "Ars Affinity perk tree must define " + nodeId);
                Object data = data(player);
                Method allocated = data.getClass().getMethod("isPerkAllocated", String.class);
                if ((Boolean) allocated.invoke(data, nodeId)) return;
                data.getClass().getMethod("addAvailablePoints", com.hollingsworth.arsnouveau.api.spell.SpellSchool.class, int.class)
                    .invoke(data, SpellSchools.MANIPULATION, 1);
                Object ok = data.getClass().getMethod("allocatePerk", node.getClass()).invoke(data, node);
                helper.assertTrue(Boolean.TRUE.equals(ok), "Ars Affinity must allocate " + nodeId);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Ars Affinity 1.1.1 perk API changed", error);
            }
        }

        static double manaTapAmount(Player player) {
            try {
                Class<?> type = Class.forName("com.github.ars_affinity.perk.AffinityPerkType");
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object tap = Enum.valueOf((Class) type, "PASSIVE_MANA_TAP");
                return ((Number) Class.forName("com.github.ars_affinity.perk.AffinityPerkHelper")
                    .getMethod("getPerkAmount", Player.class, type).invoke(null, player, tap)).doubleValue();
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Ars Affinity 1.1.1 perk helper API changed", error);
            }
        }
    }
}
