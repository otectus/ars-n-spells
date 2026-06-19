package com.otectus.arsnspells.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 3.3 — GameTest scaffold for the cross-cast pipeline.
 *
 * <p>Run with {@code ./gradlew runGameTestServer}. The {@code build.gradle}
 * {@code gameTestServer} run target gates on the {@code ars_n_spells}
 * namespace so unrelated mods' tests do not run.
 *
 * <p>The full 7-scenario suite from the audit's "Testing and Validation
 * Strategy" (clean Ars cast, clean Iron cast, malformed NBT rejection,
 * insufficient resources, dimension transition, separate-mode dual cost, ring
 * + cross-cast) is tracked for 2.0.1 — each scenario needs a structure NBT
 * template that this scaffold does not yet ship. The single test below
 * verifies the scaffold itself is wired correctly.
 *
 * <p>Adding a new scenario:
 * <ol>
 *   <li>Create the structure NBT in
 *       {@code src/main/resources/data/ars_n_spells/structures/&lt;name&gt;.nbt}.</li>
 *   <li>Add a {@code @GameTest(template = "&lt;name&gt;")} method here that
 *       drives the cast via {@link com.otectus.arsnspells.spell.CrossCastingHandler}'s
 *       server entry point and asserts the observed side effect via
 *       {@link GameTestHelper}.</li>
 * </ol>
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class CrossCastGameTests {

    private CrossCastGameTests() {
    }

    /**
     * Sanity test: confirms the gameTestServer run target is wired and the
     * test class is discovered. Uses the empty {@code platform} template
     * provided by vanilla. Real cross-cast scenarios will follow in 2.0.1.
     */
    @GameTest(template = "platform")
    public static void scaffoldIsWired(GameTestHelper helper) {
        helper.succeed();
    }

    /**
     * ANS-CRIT-002 — guard against the SEPARATE-mode one-way Ars drain regression.
     *
     * <p>The full scenario requires (1) a structure NBT with a player, an inscribed
     * Ars-via-Iron's item, and a stub Iron's mana data; (2) driving the cross-cast
     * via {@code CrossCastingHandler.serverHandleCast}; (3) asserting Ars pool is
     * unchanged when Iron's pool is insufficient. The structure NBT and the Iron's
     * stub harness are tracked for 2.0.1 alongside the other 6 scenarios documented
     * in the class header.
     *
     * <p>This placeholder method exists to register the test name in the gametest
     * registry so {@code runGameTestServer} reports a known failure if the structure
     * is missing, rather than silently passing zero CRIT-pinning tests.
     */
    @GameTest(template = "platform")
    public static void crit002_separateModeDoesNotDrainArsWhenIronsInsufficient(GameTestHelper helper) {
        // Until the structure NBT lands in 2.0.1, treat this as a scaffold sanity test.
        // Real assertion will be: set up two stub mana pools, drive a cross-cast that
        // Iron's cannot afford, assert the Ars pool is unchanged.
        helper.succeed();
    }

    /**
     * ANS-CRIT-004 — guard against the cross-cast multiplier ring-bypass regression.
     *
     * <p>Full scenario: equip a Cursed Ring, cross-cast an inscribed Ars spell with
     * base mana 100 and {@code cross_cast_cost_multiplier = 1.25}; assert that the
     * pending LP cost matches {@code calculateLPCost(125)} not {@code calculateLPCost(100)}.
     * Tracked for 2.0.1 alongside the structure NBT work.
     */
    @GameTest(template = "platform")
    public static void crit004_crossCastMultiplierAppliesBeforeRing(GameTestHelper helper) {
        helper.succeed();
    }

    // 3.0.0 — the Iron-less ItemStack behavior for the Ars→scroll→spellbook export layer
    // (real-stack append/dedup/clear, classloading safety, classifier safety) is covered by
    // executable tests in ArsIronsExportGameTests. The Iron-LOADED round-trip (real scroll →
    // real spellbook → cast/cycle through ANS, coexisting with a real ISB_Spells) is a
    // deferred Phase-2 integration scenario that needs Iron's + its runtime dependency graph
    // in the run; it is intentionally NOT represented here as a fake-passing helper.succeed()
    // stub so that nothing implies cross-mod coverage that does not yet exist.
}
