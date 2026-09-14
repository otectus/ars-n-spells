package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.SharedPoolCeiling;
import com.otectus.arsnspells.casting.CastLedger;
import com.otectus.arsnspells.casting.QuoteService;
import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.equipment.EquipmentIntegration;
import com.otectus.arsnspells.util.ManaUtil;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A spell must cost the spell's cost, on every mana mode.
 *
 * <p>Reproduces the reported HYBRID-mode drain against the real Iron's runtime rather than
 * a stub. Iron's {@code MagicData.setMana} clamps every write down to the player's
 * {@code max_mana} attribute, so if that ceiling ever sits below the pool, a cast does not
 * subtract its cost — it collapses the pool to the ceiling. HYBRID used to push only the
 * gear-derived slice of Ars's max into that attribute, and the attribute modifier is
 * transient, so the ceiling could also simply go missing (a dimension change rebuilds the
 * player). Either way the next cast, of any cost, ate everything above it.
 *
 * <p>These run only under {@code ./gradlew runGameTestServer -PwithIronsRuntimeGameTests};
 * they self-skip when Iron's is absent, since without it there is no second pool to
 * disagree with.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class SharedPoolManaGameTests {

    /** Enough glyph bonus that Ars's max clears Iron's default pool by a wide margin. */
    private static final int GLYPH_BONUS = 40;

    private SharedPoolManaGameTests() {}

    /** Non-ANS modifier used to stand in for Iron's own mage gear. */
    private static final UUID IRONS_OWN_GEAR_ID =
        UUID.fromString("0a000000-0000-0000-0000-0000000067ea");

    private static GameProfile scenarioProfile(String scenario) {
        String name = scenario.length() > 16 ? scenario.substring(0, 16) : scenario;
        return new GameProfile(
            UUID.nameUUIDFromBytes(("ans_gametest/" + scenario).getBytes(StandardCharsets.UTF_8)), name);
    }

    private static ServerPlayer preparedPlayer(GameTestHelper helper, String scenario) {
        // FakePlayerFactory caches by profile, so a shared profile would hand the same instance
        // to every test in this class. Each scenario takes its own profile, and still resets the
        // state it mutates, or an earlier test's leftover ceiling modifier silently becomes the
        // next test's baseline.
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), scenarioProfile(scenario));
        EquipmentIntegration.clearArsBonusesFromIrons(player);
        removeIronsOwnGear(player);
        ManaUtil.getNativeMana(player).ifPresent(cap -> {
            cap.setGlyphBonus(0);
            cap.setBookTier(0);
        });
        // A MagicData with no serverPlayer does not clamp at all, which would make every
        // assertion below pass for the wrong reason.
        MagicData.getPlayerMagicData(player).setServerPlayer(player);
        MagicData.getPlayerMagicData(player).setMana(0.0f);
        return player;
    }

    private static void giveIronsOwnGear(ServerPlayer player, double amount) {
        AttributeInstance instance = player.getAttribute(AttributeRegistry.MAX_MANA.get());
        if (instance != null) {
            instance.addTransientModifier(new AttributeModifier(
                IRONS_OWN_GEAR_ID, "Test Iron's Gear", amount, AttributeModifier.Operation.ADDITION));
        }
    }

    private static void removeIronsOwnGear(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(AttributeRegistry.MAX_MANA.get());
        if (instance != null && instance.getModifier(IRONS_OWN_GEAR_ID) != null) {
            instance.removeModifier(IRONS_OWN_GEAR_ID);
        }
    }

    private static float ironsCeiling(ServerPlayer player) {
        return (float) player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
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
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_hybridCast_deductsOnlyTheCost_afterTheCeilingDrifted(
            GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper, "mana_drift");
        IManaCap cap = ManaUtil.getNativeMana(player).orElse(null);
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
            EquipmentIntegration.clearArsBonusesFromIrons(player);
            float ceilingBefore = ironsCeiling(player);
            if (!SharedPoolCeiling.wouldDestroyMana(poolBefore, ceilingBefore)) {
                helper.fail("test setup failed: the hazard was never armed (pool " + poolBefore
                    + " is not above ceiling " + ceilingBefore + "), so this proves nothing");
                return;
            }

            float cost = 50.0f;
            if (!BridgeManager.consumeManaForMode(player, cost, ResourceUnit.ARS_MANA)) {
                helper.fail("a " + cost + " cost must be affordable from a pool of " + poolBefore);
                return;
            }

            float expected = poolBefore - cost;
            float actual = data.getMana();
            if (Math.abs(actual - expected) > 0.01f) {
                helper.fail("a " + cost + " mana spell must leave " + expected + ", left " + actual
                    + " (ceiling was " + ceilingBefore + " — the clamp ate the surplus)");
            }
        });
        helper.succeed();
    }

    /** Casting must not shrink a pool Iron's own gear made larger than Ars's max. */
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_hybridCast_leavesIronsOwnLargerPoolAlone(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper, "mana_glyph");
        IManaCap cap = ManaUtil.getNativeMana(player).orElse(null);
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
                if (!BridgeManager.consumeManaForMode(player, cost, ResourceUnit.ARS_MANA)) {
                    helper.fail("a " + cost + " cost must be affordable from a pool of " + poolBefore);
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

    /** Distinct carrier identities so the ledger scenarios cannot find each other's attempts. */
    private static final String COST_READ_CARRIER = "ans_gametest:cost_reads";
    private static final String COMMIT_CARRIER = "ans_gametest:commit";
    private static final String CANCEL_CARRIER = "ans_gametest:cancel";

    /**
     * V01: asking the price must be free, however many times you ask.
     *
     * <p>Upstream Ars 4.12.7 builds a fresh {@code SpellCostCalcEvent} on every
     * {@code getResolveCost()} call, and the SEPARATE-mode Iron's share used to be consumed
     * inside that calculation. Ten reads took the Iron's leg ten times.
     */
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_tenCostReadsChangeNoBalance(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper, "mana_reads");
        runInHybrid(helper, () -> {
            MagicData data = MagicData.getPlayerMagicData(player);
            EquipmentIntegration.syncIronsMaxToArs(player, 500.0f);
            data.setMana(400.0f);
            float before = data.getMana();

            int first = -1;
            for (int i = 0; i < 10; i++) {
                int quoted = CrossCastingHandler.quoteArsCostForEvent(new Object(),
                    player.getUUID(), COST_READ_CARRIER, CarrierPolicy.NATIVE_ONLY, 100,
                    QuoteService.currentRules(), player.level().getGameTime());
                if (first < 0) {
                    first = quoted;
                } else if (quoted != first) {
                    helper.fail("cost read " + (i + 1) + " answered " + quoted
                        + " but the first answered " + first + "; a price must not depend on "
                        + "how many times it was asked");
                    return;
                }
            }

            float after = data.getMana();
            if (Math.abs(after - before) > 0.01f) {
                helper.fail("ten cost reads moved the pool from " + before + " to " + after
                    + "; asking the price must not cost money");
            }
            CastLedger.findOpen(player.getUUID(), COST_READ_CARRIER)
                .ifPresent(a -> CastLedger.cancel(a, CastLedger.forPlayer(player)));
        });
        helper.succeed();
    }

    /** V01: a settled cast takes the quote, exactly, and never gives it back. */
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_successfulCastDebitsExactlyTheQuote(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper, "mana_commit");
        runInHybrid(helper, () -> {
            MagicData data = MagicData.getPlayerMagicData(player);
            EquipmentIntegration.syncIronsMaxToArs(player, 500.0f);
            data.setMana(400.0f);
            float before = data.getMana();

            CostQuote quote = QuoteService.quoteNativeCast(
                ResourceUnit.ARS_MANA, 100, QuoteService.currentRules());
            float expectedLeg = QuoteService.legAsFloat(quote, ResourceUnit.ARS_MANA);

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
     * V01: a cancelled cast is refunded once, not once per exit path.
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
        ServerPlayer player = preparedPlayer(helper, "mana_cancel");
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

    /**
     * Repeated casts must stay loss-exact, not just the first one.
     *
     * <p>Owns its own batch (T0.4's rule). This is the one scenario here that deliberately
     * strips the ceiling and then leans on {@code ensureSharedPoolCeiling} putting it back on
     * the deduction path, and that guard only runs in HYBRID. The mana mode is a single global
     * the whole run shares and every test in {@code ans_mana_config} ticks concurrently, so a
     * sibling's {@code finally} restoring the mode landed between this test's casts: the
     * ceiling was never re-applied, Iron's clamped the 700 pool to its own 100 max on the first
     * write, and the remaining four casts spent that down to 20.
     */
    @GameTest(template = "platform", batch = "ans_mana_ceiling_drift")
    public static void ironsLoaded_hybridRepeatedCasts_stayLossExact(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper, "mana_book");
        IManaCap cap = ManaUtil.getNativeMana(player).orElse(null);
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
            EquipmentIntegration.clearArsBonusesFromIrons(player);

            float cost = 20.0f;
            int casts = 5;
            for (int i = 0; i < casts; i++) {
                if (!BridgeManager.consumeManaForMode(player, cost, ResourceUnit.ARS_MANA)) {
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
}
