package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.TestPaths;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.ResourceAccess;
import com.otectus.arsnspells.contract.ResourceUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit V06 - regeneration must be intercepted at the regeneration site, not by disabling the
 * capability's whole additive surface.
 *
 * <p>The defect: {@code MixinManaCapability} suppressed Ars's regen by cancelling
 * {@code ManaCap.addMana} outright in the shared-pool modes and returning the unchanged balance.
 * But {@code addMana} is not the regen tick - it is every additive mutation of Ars mana there is.
 * A potion, a ritual, an artifact or another integration mod granting the player mana was
 * silently discarded along with the regen, with no log line and no way to tell.
 *
 * <p>In the pinned Ars 5.13.1.1400 the regeneration tick is one specific call:
 * {@code ManaCapEvents.playerOnTick(PlayerTickEvent$Pre)} invokes {@code ManaCap.addMana(D)D}
 * exactly once, right after the {@code getManaRegen} / {@code MEAN_TPS} / {@code REGEN_INTERVAL}
 * division that computes the per-tick amount. That is the site now redirected, and it is the only
 * {@code addMana} call in the method, so the redirect needs no ordinal.
 *
 * <p>The routing is exercised against a fake {@link ResourceAccess}, so this needs no Minecraft.
 */
class ManaRegenInterceptionTest {

    /** A single-pool stand-in that records every movement. */
    private static final class FakePool implements ResourceAccess {
        final Map<ResourceUnit, Double> balances = new EnumMap<>(ResourceUnit.class);
        int credits;
        int debits;

        FakePool() {
            for (ResourceUnit unit : ResourceUnit.values()) {
                balances.put(unit, 100.0d);
            }
        }

        @Override public double current(UUID p, ResourceUnit u) { return balances.get(u); }
        @Override public double max(UUID p, ResourceUnit u) { return 1000.0d; }

        @Override
        public double debit(UUID p, ResourceUnit u, double amount) {
            debits++;
            double moved = Math.min(amount, balances.get(u));
            balances.put(u, balances.get(u) - moved);
            return moved;
        }

        @Override
        public double credit(UUID p, ResourceUnit u, double amount) {
            credits++;
            balances.put(u, balances.get(u) + amount);
            return amount;
        }
    }

    private static final UUID PLAYER = UUID.fromString("0a000000-0000-0000-0000-0000000006e6");

    @AfterEach
    void clearRouting() {
        BridgeManager.testSetRouting(null);
    }

    // ------------------------------------------------------------------
    //  A third-party grant reaches the authoritative pool
    // ------------------------------------------------------------------

    @Test
    void aThirdPartyAddManaInIssPrimary_movesTheAuthoritativePoolByFifty() {
        BridgeManager.testSetMode(ManaUnificationMode.ISS_PRIMARY);
        assertEquals(ResourceUnit.IRONS_MANA, ManaMutationRouter.authoritativeUnit(),
            "ISS_PRIMARY makes the Iron's pool authoritative");

        FakePool pool = new FakePool();
        double before = pool.balances.get(ResourceUnit.IRONS_MANA);

        double now = ManaMutationRouter.routeAdd(
            PLAYER, ResourceUnit.IRONS_MANA, pool, 50.0d);

        assertEquals(before + 50.0d, pool.balances.get(ResourceUnit.IRONS_MANA), 1.0e-9d,
            "a +50 grant must move the authoritative pool by +50, in Iron's units. The old "
                + "blanket no-op discarded it and told the caller nothing had happened");
        assertEquals(before + 50.0d, now, 1.0e-9d,
            "ManaCap.addMana returns the balance after the add; the routed call must too");
        assertEquals(100.0d, pool.balances.get(ResourceUnit.ARS_MANA), 1.0e-9d,
            "and must not also credit the Ars pool - a shared pool is one pool counted once");
    }

    @Test
    void aThirdPartyRemoveMana_alsoReachesTheAuthoritativePool() {
        BridgeManager.testSetMode(ManaUnificationMode.ISS_PRIMARY);
        FakePool pool = new FakePool();

        double now = ManaMutationRouter.routeRemove(
            PLAYER, ResourceUnit.IRONS_MANA, pool, 30.0d);

        assertEquals(70.0d, pool.balances.get(ResourceUnit.IRONS_MANA), 1.0e-9d,
            "a third-party drain must drain the pool that actually holds this player's mana");
        assertEquals(70.0d, now, 1.0e-9d);
        assertEquals(1, pool.debits, "exactly one movement");
    }

    // ------------------------------------------------------------------
    //  Regen is suppressed only in shared modes
    // ------------------------------------------------------------------

    @Test
    void regenIsSuppressedOnlyInSharedPoolModes() {
        assertTrue(ManaMutationRouter.suppressNativeRegen(ManaUnificationMode.ISS_PRIMARY, true),
            "ISS_PRIMARY shares one pool; letting Ars regen it too mints mana");
        assertTrue(ManaMutationRouter.suppressNativeRegen(ManaUnificationMode.ARS_PRIMARY, true),
            "ARS_PRIMARY likewise");
        assertTrue(ManaMutationRouter.suppressNativeRegen(ManaUnificationMode.HYBRID, true),
            "HYBRID likewise");

        assertFalse(ManaMutationRouter.suppressNativeRegen(ManaUnificationMode.SEPARATE, true),
            "SEPARATE keeps two real pools, and each must go on regenerating on its own");
        assertFalse(ManaMutationRouter.suppressNativeRegen(ManaUnificationMode.DISABLED, true),
            "DISABLED must not touch Ars's regeneration at all");
        for (ManaUnificationMode mode : ManaUnificationMode.values()) {
            assertFalse(ManaMutationRouter.suppressNativeRegen(mode, false),
                mode + ": with unification off, Ars regenerates its own pool");
        }
    }

    // ------------------------------------------------------------------
    //  The per-player, per-direction guard
    // ------------------------------------------------------------------

    @Test
    void aReadMayNestInsideAWrite() {
        ManaMutationRouter.enter(PLAYER, ManaMutationRouter.Direction.WRITE);
        try {
            assertFalse(ManaMutationRouter.isGuarded(PLAYER, ManaMutationRouter.Direction.READ),
                "routing a write reads the pool back to report the new balance; with one flag "
                    + "per player that read looked like recursion and fell through to stale "
                    + "native data");
            assertTrue(ManaMutationRouter.isGuarded(PLAYER, ManaMutationRouter.Direction.WRITE),
                "a write inside a write is genuine recursion and must be caught");
        } finally {
            ManaMutationRouter.exit(PLAYER, ManaMutationRouter.Direction.WRITE);
        }
        assertFalse(ManaMutationRouter.isGuarded(PLAYER, ManaMutationRouter.Direction.WRITE),
            "the guard must be released");
    }

    @Test
    void theGuardIsPerPlayer() {
        UUID other = UUID.fromString("0a000000-0000-0000-0000-0000000006e7");
        ManaMutationRouter.enter(PLAYER, ManaMutationRouter.Direction.WRITE);
        try {
            assertFalse(ManaMutationRouter.isGuarded(other, ManaMutationRouter.Direction.WRITE),
                "ANS-HIGH-010: one player's in-flight call must not suppress interception for "
                    + "every other player on the thread - an AoE spell reads other players' mana");
        } finally {
            ManaMutationRouter.exit(PLAYER, ManaMutationRouter.Direction.WRITE);
        }
    }

    @Test
    void aReentrantWrite_movesNothingButStillReportsTheBalance() {
        FakePool pool = new FakePool();
        ManaMutationRouter.enter(PLAYER, ManaMutationRouter.Direction.WRITE);
        try {
            double reported =
                ManaMutationRouter.routeAdd(PLAYER, ResourceUnit.IRONS_MANA, pool, 50.0d);
            assertEquals(100.0d, reported, 1.0e-9d,
                "a re-entrant write reports the current balance rather than lying with a zero");
            assertEquals(0, pool.credits, "and moves nothing, or the grant lands twice");
        } finally {
            ManaMutationRouter.exit(PLAYER, ManaMutationRouter.Direction.WRITE);
        }
    }

    // ------------------------------------------------------------------
    //  The injections are split, and the tick one is site-specific
    // ------------------------------------------------------------------

    @Test
    void theRegenSuppressionTargetsTheTickSite_notAddManaGenerally() throws IOException {
        String src = Files.readString(TestPaths.of(
            "src/main/java/com/otectus/arsnspells/mixin/ars/MixinManaCapEventsRegen.java"));
        assertTrue(src.contains("method = \"playerOnTick\""),
            "the regen suppression must target the tick handler, which is the only thing that "
                + "regenerates - not ManaCap.addMana, which is every additive mutation there is");
        assertTrue(src.contains("@At(\"HEAD\")") && src.contains("@At(\"RETURN\")"),
            "the tick must be bracketed, so an addMana arriving inside it can be told apart from "
                + "a third-party grant arriving outside it");
        // The annotation form, not the word: the javadoc discusses @Redirect at length
        // precisely to record why it is not used.
        assertFalse(src.contains("@Redirect("),
            "a @Redirect on the addMana invocation would say this more directly, but Mixin "
                + "rejects an instruction-level injection point outright when another mod "
                + "@Overwrite-merges the target, during PREPARE, before require is consulted - "
                + "so require = 0 does not soften it and the whole load aborts");
        assertTrue(src.contains("Direction.REGEN_TICK"),
            "the bracket is what scopes the suppression to the regeneration site");
    }

    @Test
    void theCapabilityMixinNoLongerBlanketSuppressesAddMana() throws IOException {
        String src = Files.readString(TestPaths.of(
            "src/main/java/com/otectus/arsnspells/mixin/ars/MixinManaCapability.java"));
        int from = src.indexOf("private void arsnspells$addMana(");
        assertTrue(from > 0, "the addMana intercept must still exist");
        String body = src.substring(from, src.indexOf("\n    }", from));
        assertTrue(body.contains("ManaMutationRouter.routeAdd"),
            "addMana must route the grant to the authoritative pool, not discard it");
        assertTrue(body.contains("Direction.REGEN_TICK"),
            "and may only decline to do so while Ars's own regeneration tick is in flight");
    }

    @Test
    void bothInjectionsAreRegistered() throws IOException {
        String config = Files.readString(
            TestPaths.of("src/main/resources/ars_n_spells.mixins.json"));
        assertTrue(config.contains("ars.MixinManaCapability"),
            "the capability mixin stays registered");
        assertTrue(config.contains("ars.MixinManaCapEventsRegen"),
            "the tick-site mixin must be registered, or the split silently leaves regen running");
        assertTrue(config.contains("\"required\": true"),
            "these mixins are required=true; a bad target is a hard boot failure, which is the "
                + "correct outcome rather than a silently mispriced world");
    }
}
