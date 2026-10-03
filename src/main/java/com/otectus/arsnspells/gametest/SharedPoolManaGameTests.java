package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.common.capability.ManaCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.SharedPoolCeiling;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.casting.CastLedger;
import com.otectus.arsnspells.casting.QuoteService;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.equipment.EquipmentIntegration;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * A spell must cost the spell's cost, on every mana mode.
 *
 * <p>Reproduces the reported HYBRID-mode drain against the real Iron's runtime rather than a
 * stub. Iron's {@code MagicData.setMana} clamps every write down to the player's
 * {@code max_mana} attribute, so if that ceiling ever sits below the pool, a cast does not
 * subtract its cost - it collapses the pool to the ceiling. HYBRID used to push only the
 * gear-derived slice of Ars's max into that attribute, and the attribute modifier is
 * transient, so the ceiling could also simply go missing (a dimension change rebuilds the
 * player). Either way the next cast, of any cost, ate everything above it.
 *
 * <p>These run only under {@code ./gradlew runGameTestServer -PwithIronsRuntimeGameTests}; they
 * self-skip when Iron's is absent, since without it there is no second pool to disagree with.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class SharedPoolManaGameTests {

    private static final GameProfile FAKE_PROFILE =
        new GameProfile(UUID.fromString("0a000000-0000-0000-0000-00000000c3a5"), "ans_mana_test");

    /** Enough glyph bonus that Ars's max clears Iron's default pool by a wide margin. */
    private static final int GLYPH_BONUS = 40;

    /** Non-ANS modifier used to stand in for Iron's own mage gear. */
    private static final ResourceLocation IRONS_OWN_GEAR_ID =
        ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "gametest_irons_own_gear");

    private SharedPoolManaGameTests() {}

    private static ServerPlayer preparedPlayer(GameTestHelper helper) {
        // FakePlayerFactory caches by profile, so the same instance is handed to every test in
        // this class. Reset the state each test mutates, or an earlier test's leftover ceiling
        // modifier silently becomes the next test's baseline.
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ans_mana_test"));
        EquipmentIntegration.clearAll(player);
        removeIronsOwnGear(player);
        ManaCap cap = CapabilityRegistry.getMana(player);
        if (cap != null) {
            cap.setGlyphBonus(0);
            cap.setBookTier(0);
        }
        // A MagicData with no serverPlayer does not clamp at all, which would make every
        // assertion below pass for the wrong reason.
        MagicData data = MagicData.getPlayerMagicData(player);
        data.setServerPlayer(player);
        data.setMana(0.0f);
        return player;
    }

    private static void giveIronsOwnGear(ServerPlayer player, double amount) {
        AttributeInstance instance = player.getAttribute(AttributeRegistry.MAX_MANA);
        if (instance != null) {
            instance.addTransientModifier(new AttributeModifier(
                IRONS_OWN_GEAR_ID, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void removeIronsOwnGear(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(AttributeRegistry.MAX_MANA);
        if (instance != null && instance.getModifier(IRONS_OWN_GEAR_ID) != null) {
            instance.removeModifier(IRONS_OWN_GEAR_ID);
        }
    }

    private static float ironsCeiling(ServerPlayer player) {
        return (float) player.getAttributeValue(AttributeRegistry.MAX_MANA);
    }

    private static void runInHybrid(GameTestHelper helper, Runnable body) {
        String previous = AnsConfig.MANA_UNIFICATION_MODE.get();
        try {
            AnsConfig.MANA_UNIFICATION_MODE.set(ManaUnificationMode.HYBRID.getConfigName());
            BridgeManager.refreshMode();
            if (BridgeManager.getCurrentMode() != ManaUnificationMode.HYBRID) {
                helper.fail("could not enter HYBRID mode; got " + BridgeManager.getCurrentMode());
                return;
            }
            body.run();
        } finally {
            AnsConfig.MANA_UNIFICATION_MODE.set(previous);
            BridgeManager.refreshMode();
        }
    }

    /**
     * The reported bug, end to end: a full Ars-sized pool, a small spell, and a ceiling that
     * has drifted back down to Iron's own. The cast must cost the cost.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_hybridCast_deductsOnlyTheCost_afterTheCeilingDrifted(
            GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        ManaCap cap = CapabilityRegistry.getMana(player);
        if (cap == null) {
            helper.fail("Ars mana capability missing on the test player");
            return;
        }

        runInHybrid(helper, () -> {
            cap.setGlyphBonus(GLYPH_BONUS);
            float arsMax = EquipmentIntegration.arsRealMaxMana(player);

            // Fill the shared pool to Ars's max, which needs the ceiling raised first.
            EquipmentIntegration.syncIronsMaxToArs(player, arsMax);
            MagicData data = MagicData.getPlayerMagicData(player);
            data.setMana(arsMax);
            float poolBefore = data.getMana();
            if (poolBefore < arsMax - 0.5f) {
                helper.fail("test setup failed: could not fill the pool to Ars's max " + arsMax
                    + ", got " + poolBefore);
                return;
            }

            // Now lose the ceiling the way a dimension change or a stale recompute loses it.
            EquipmentIntegration.clearAll(player);
            float ceilingBefore = ironsCeiling(player);
            if (!SharedPoolCeiling.wouldDestroyMana(poolBefore, ceilingBefore)) {
                helper.fail("test setup failed: the hazard was never armed (pool " + poolBefore
                    + " is not above ceiling " + ceilingBefore + "), so this proves nothing");
                return;
            }

            float cost = 50.0f;
            if (!BridgeManager.consumeManaForMode(player, cost, true)) {
                helper.fail("a " + cost + " cost must be affordable from a pool of " + poolBefore);
                return;
            }

            float expected = poolBefore - cost;
            float actual = data.getMana();
            if (Math.abs(actual - expected) > 0.01f) {
                helper.fail("a " + cost + " mana spell must leave " + expected + ", left " + actual
                    + " (ceiling was " + ceilingBefore + " - the clamp ate the surplus)");
            }
        });
        helper.succeed();
    }

    /** Casting must not shrink a pool Iron's own gear made larger than Ars's max. */
    @GameTest(template = "platform")
    public static void ironsLoaded_hybridCast_leavesIronsOwnLargerPoolAlone(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        ManaCap cap = CapabilityRegistry.getMana(player);
        if (cap == null) {
            helper.fail("Ars mana capability missing on the test player");
            return;
        }

        runInHybrid(helper, () -> {
            // Ars asks for a small pool; Iron's own gear asks for a much larger one.
            cap.setGlyphBonus(0);
            giveIronsOwnGear(player, 600.0);
            try {
                float arsMax = EquipmentIntegration.arsRealMaxMana(player);
                float ironsOwn = ironsCeiling(player);
                if (ironsOwn <= arsMax) {
                    helper.fail("test setup failed: Iron's own max " + ironsOwn
                        + " must exceed Ars's " + arsMax + " for this test to mean anything");
                    return;
                }

                EquipmentIntegration.syncIronsMaxToArs(player, arsMax);
                float ceiling = ironsCeiling(player);
                float expectedCeiling = (float) SharedPoolCeiling.resultingCeiling(ironsOwn, arsMax);
                if (Math.abs(ceiling - expectedCeiling) > 0.01f) {
                    helper.fail("the shared ceiling must be max(Ars " + arsMax + ", Iron's "
                        + ironsOwn + ") = " + expectedCeiling + ", got " + ceiling);
                    return;
                }

                MagicData data = MagicData.getPlayerMagicData(player);
                data.setMana(ceiling);
                float poolBefore = data.getMana();

                float cost = 10.0f;
                if (!BridgeManager.consumeManaForMode(player, cost, true)) {
                    helper.fail("a " + cost + " cost must be affordable from a pool of "
                        + poolBefore);
                    return;
                }
                float actual = data.getMana();
                if (Math.abs(actual - (poolBefore - cost)) > 0.01f) {
                    helper.fail("Iron's own pool must spend normally: expected "
                        + (poolBefore - cost) + ", got " + actual);
                }
            } finally {
                removeIronsOwnGear(player);
            }
        });
        helper.succeed();
    }

    /** Repeated casts must stay loss-exact, not just the first one. */
    @GameTest(template = "platform")
    public static void ironsLoaded_hybridRepeatedCasts_stayLossExact(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        ManaCap cap = CapabilityRegistry.getMana(player);
        if (cap == null) {
            helper.fail("Ars mana capability missing on the test player");
            return;
        }

        runInHybrid(helper, () -> {
            cap.setGlyphBonus(GLYPH_BONUS);
            float arsMax = EquipmentIntegration.arsRealMaxMana(player);
            EquipmentIntegration.syncIronsMaxToArs(player, arsMax);

            MagicData data = MagicData.getPlayerMagicData(player);
            data.setMana(arsMax);
            float poolBefore = data.getMana();
            EquipmentIntegration.clearAll(player);

            float cost = 20.0f;
            int casts = 5;
            for (int i = 0; i < casts; i++) {
                if (!BridgeManager.consumeManaForMode(player, cost, true)) {
                    helper.fail("cast " + i + " should have been affordable");
                    return;
                }
            }
            float expected = poolBefore - cost * casts;
            float actual = data.getMana();
            if (Math.abs(actual - expected) > 0.01f) {
                helper.fail(casts + " casts of " + cost + " from " + poolBefore + " must leave "
                    + expected + ", left " + actual);
            }
        });
        helper.succeed();
    }

    /** Distinct carrier identities so the ledger scenarios cannot find each other's attempts. */
    private static final String COMMIT_CARRIER = "ans_gametest:commit";
    private static final String CANCEL_CARRIER = "ans_gametest:cancel";

    /** V01: a settled cast takes the quote, exactly, and never gives it back. (Forge parity.) */
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_successfulCastDebitsExactlyTheQuote(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        runInHybrid(helper, () -> {
            MagicData data = MagicData.getPlayerMagicData(player);
            EquipmentIntegration.syncIronsMaxToArs(player, 500.0f);
            data.setMana(400.0f);
            float before = data.getMana();

            CostQuote quote = QuoteService.quoteNativeCast(
                ResourceUnit.ARS_MANA, 100, QuoteService.currentRules());
            float expectedLeg = QuoteService.legAsFloat(quote, ResourceUnit.IRONS_MANA);

            CastAttempt attempt = CastLedger.open(player.getUUID(), COMMIT_CARRIER, 0, quote,
                player.level().getGameTime());
            CastLedger.reserve(attempt, CastLedger.forPlayer(player));

            float afterReserve = data.getMana();
            if (Math.abs((before - afterReserve) - expectedLeg) > 0.01f) {
                helper.fail("reserving a " + expectedLeg + " quote moved the pool by "
                    + (before - afterReserve));
                return;
            }

            CastLedger.commitAndComplete(attempt);

            float afterCommit = data.getMana();
            if (Math.abs(afterCommit - afterReserve) > 0.01f) {
                helper.fail("committing must not move the pool again: " + afterReserve
                    + " became " + afterCommit + ". The reservation is the payment");
            }
        });
        helper.succeed();
    }

    /**
     * V01: a cancelled cast is refunded once, not once per exit path. (Forge parity.)
     *
     * <p>A long cast that was interrupted and then also ended normally used to run the release
     * path twice and credit the player twice, because nothing tied a refund to the charge it
     * reversed.
     */
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_cancellationReleasesTheReservationExactlyOnce(
            GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        runInHybrid(helper, () -> {
            MagicData data = MagicData.getPlayerMagicData(player);
            EquipmentIntegration.syncIronsMaxToArs(player, 500.0f);
            data.setMana(400.0f);
            float before = data.getMana();

            CostQuote quote = QuoteService.quoteNativeCast(
                ResourceUnit.ARS_MANA, 100, QuoteService.currentRules());
            CastAttempt attempt = CastLedger.open(player.getUUID(), CANCEL_CARRIER, 0, quote,
                player.level().getGameTime());
            CastLedger.reserve(attempt, CastLedger.forPlayer(player));

            CastLedger.cancel(attempt, CastLedger.forPlayer(player));
            float afterFirstRelease = data.getMana();
            if (Math.abs(afterFirstRelease - before) > 0.01f) {
                helper.fail("a cancelled cast must return exactly what it took: " + before
                    + " became " + afterFirstRelease);
                return;
            }

            // A second exit path arrives. It must settle the attempt and pay nothing.
            CastLedger.cancel(attempt, CastLedger.forPlayer(player));
            float afterSecondRelease = data.getMana();
            if (Math.abs(afterSecondRelease - afterFirstRelease) > 0.01f) {
                helper.fail("the second release credited "
                    + (afterSecondRelease - afterFirstRelease) + " more mana; a refund must "
                    + "happen exactly once or a cancelled cast prints mana");
            }
        });
        helper.succeed();
    }
}
