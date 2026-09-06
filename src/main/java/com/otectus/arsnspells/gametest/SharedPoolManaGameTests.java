package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.common.capability.ManaCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.SharedPoolCeiling;
import com.otectus.arsnspells.compat.CompatIds;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.casting.AnsQuotes;
import com.otectus.arsnspells.casting.AttemptLedgerService;
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

import java.nio.charset.StandardCharsets;
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
 *
 * <p><b>Every scenario that enters HYBRID sits in a batch of its own, and those batches must not
 * be merged.</b> {@code AnsConfig.MANA_UNIFICATION_MODE} is process-global while GameTest runs the
 * tests inside a batch concurrently, so a sibling's {@code finally} restoring the mode lands in
 * the middle of this test's casts. That is not a cosmetic race:
 * {@code EquipmentIntegration.ensureSharedPoolCeiling} early-returns on any non-HYBRID mode, so a
 * ceiling a test deliberately strips is then never rebuilt, and Iron's own down-clamp eats the
 * surplus - the failure reads as bad arithmetic ("5 casts of 20.0 from 700.0 must leave 600.0,
 * left 20.0") when the arithmetic was never wrong. A batch per mode-mutating scenario is the only
 * isolation GameTest offers. {@link #runInHybrid} restores the <em>prior</em> value, not a
 * hard-coded one, from a {@code finally}, so a test that bails out cannot poison a later batch.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class SharedPoolManaGameTests {

    /** Enough glyph bonus that Ars's max clears Iron's default pool by a wide margin. */
    private static final int GLYPH_BONUS = 40;

    /** Non-ANS modifier used to stand in for Iron's own mage gear. */
    private static final ResourceLocation IRONS_OWN_GEAR_ID =
        ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "gametest_irons_own_gear");

    private SharedPoolManaGameTests() {}

    /**
     * One player per {@code scenario}, never a shared one: {@code FakePlayerFactory} caches by
     * {@link GameProfile}, so a single profile hands every concurrently-running test the same
     * entity - and these tests set a max-mana modifier and a pool balance on it. The UUID is
     * derived from the scenario name so it is stable across runs and unique across scenarios.
     */
    private static ServerPlayer preparedPlayer(GameTestHelper helper, String scenario) {
        GameProfile profile = new GameProfile(
            UUID.nameUUIDFromBytes(("ans_mana_gametest/" + scenario).getBytes(StandardCharsets.UTF_8)),
            "ans_mana_" + scenario);
        // The factory still caches that instance for the length of the run, so reset the state
        // each test mutates or a leftover ceiling modifier silently becomes the next baseline.
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), profile);
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
     *
     * <p>Own batch: it mutates the global mana mode and strips the ceiling. See the class note.
     */
    @GameTest(template = "platform", batch = "ans_mana_hybrid_cast")
    public static void ironsLoaded_hybridCast_deductsOnlyTheCost_afterTheCeilingDrifted(
            GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper, "ceiling_drifted");
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
            if (!BridgeManager.consumeManaForMode(player, cost, ResourceUnit.ARS_MANA)) {
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

    /**
     * Casting must not shrink a pool Iron's own gear made larger than Ars's max.
     *
     * <p>Own batch: it mutates the global mana mode. See the class note.
     */
    @GameTest(template = "platform", batch = "ans_mana_irons_own_ceiling")
    public static void ironsLoaded_hybridCast_leavesIronsOwnLargerPoolAlone(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper, "irons_own_pool");
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
                if (!BridgeManager.consumeManaForMode(player, cost, ResourceUnit.ARS_MANA)) {
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

    /**
     * Repeated casts must stay loss-exact, not just the first one.
     *
     * <p><b>Own batch, and this is the scenario the batching exists for.</b> It fills the pool to
     * Ars's max, strips the ceiling with {@code clearAll}, and then casts five times with no
     * further ceiling repair. If a concurrently-running sibling restores the mana mode away from
     * HYBRID anywhere in that window, {@code EquipmentIntegration.ensureSharedPoolCeiling}
     * early-returns and Iron's clamp collapses the pool to its base max. Do not fold this back
     * into a shared batch.
     */
    @GameTest(template = "platform", batch = "ans_mana_ceiling_drift")
    public static void ironsLoaded_hybridRepeatedCasts_stayLossExact(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper, "repeated_casts");
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
    // ------------------------------------------------------------------
    //  Audit V01: a cost query is free, and a cast costs exactly its quote
    // ------------------------------------------------------------------

    /**
     * Ten cost reads must change no balance.
     *
     * <p>The defect: Ars 5.13.1 asks what a spell costs several times per cast - {@code
     * getResolveCost()} posts a fresh {@code SpellCostCalcEvent.Pre}, {@code getExpendedCost()} a
     * fresh {@code Post} - and the old cost-calc handler <em>pre-paid the Iron's leg</em> on the
     * first one. Merely asking the price moved mana, so a tooltip or a can-I-cast check drained
     * the player. Ten reads here stand in for that repetition against the real runtime pools.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_tenCostReads_changeNoBalance(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper, "ten_cost_reads");
        MagicData data = MagicData.getPlayerMagicData(player);
        data.setMana(200.0f);

        CostRules rules = AnsQuotes.rules(player);
        CostQuote quote = AnsQuotes.quote(40, ResourceUnit.IRONS_MANA,
            CarrierPolicy.REUSABLE_BOOK_SEMANTICS, rules);
        CastAttempt attempt = AttemptLedgerService.open(player, "gametest_carrier#0", 1, quote,
            player.level().getGameTime());
        try {
            float before = data.getMana();
            int first = AnsQuotes.legAsInt(quote, ResourceUnit.IRONS_MANA, rules);
            for (int i = 0; i < 10; i++) {
                int again = AnsQuotes.legAsInt(attempt.quote(), ResourceUnit.IRONS_MANA, rules);
                if (again != first) {
                    helper.fail("read " + i + " answered " + again + " but the first answered "
                        + first + " - a price that depends on how often it is asked is not a price");
                    return;
                }
            }
            if (Math.abs(data.getMana() - before) > 0.01f) {
                helper.fail("ten cost reads moved the pool from " + before + " to " + data.getMana()
                    + " - asking the price must never pay it (audit V01)");
                return;
            }
        } finally {
            AttemptLedgerService.fail(attempt, player);
        }
        helper.succeed();
    }

    /**
     * A successful cast must debit exactly the quote - no more, and not twice.
     *
     * <p>Run against the real Iron's pool, because the arithmetic is not the risk here: Iron's
     * clamps every mana write down to the {@code max_mana} attribute, so a reservation can move
     * less than it asked for. The ledger records what actually moved, and this asserts the pool
     * agrees with that record rather than with the quote.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_successfulCast_debitsExactlyTheQuote(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper, "successful_cast");
        MagicData data = MagicData.getPlayerMagicData(player);
        data.setMana(200.0f);

        CostRules rules = AnsQuotes.rules(player);
        CostQuote quote = AnsQuotes.quote(40, ResourceUnit.IRONS_MANA,
            CarrierPolicy.REUSABLE_BOOK_SEMANTICS, rules);
        CastAttempt attempt = AttemptLedgerService.open(player, "gametest_carrier#1", 1, quote,
            player.level().getGameTime());

        float before = data.getMana();
        AttemptLedgerService.reserve(attempt, player);
        AttemptLedgerService.commit(attempt);
        AttemptLedgerService.complete(attempt);

        double recorded = 0.0d;
        for (var leg : attempt.paidLegs()) {
            if (leg.unit() == ResourceUnit.IRONS_MANA) {
                recorded += leg.amount();
            }
        }
        float actual = before - data.getMana();
        if (Math.abs(actual - recorded) > 0.01f) {
            helper.fail("the pool fell by " + actual + " but the ledger recorded a payment of "
                + recorded + " - a payment the pool disagrees with is what a refund then invents "
                + "or loses mana against");
            return;
        }
        if (recorded <= 0.0d) {
            helper.fail("a 40-mana cross-cast from a 200 pool must actually cost something; "
                + "recorded " + recorded);
            return;
        }
        if (!AttemptLedgerService.openFor(player).isEmpty()) {
            helper.fail("a completed attempt must be forgotten, not left holding a reservation");
            return;
        }
        helper.succeed();
    }
}
