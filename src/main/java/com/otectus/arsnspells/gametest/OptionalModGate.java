package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.ModPresence;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The single seam every optional-mod GameTest goes through, so a self-skip stops looking
 * like a pass.
 *
 * <p><b>Why.</b> GameTest has no notion of "skipped": a test that calls
 * {@code helper.succeed()} because Iron's is absent is recorded by the harness exactly like
 * one that exercised the whole integration. The audit's Iron's-less run therefore reported
 * "71 required tests passed" while most of the Iron's-facing scenarios never executed a line
 * of the code they name. Routing every one of those guards through this class means
 * {@link ScenarioReport} can say which of the two actually happened, and CI can read that
 * instead of the pass count.
 *
 * <p><b>Use.</b> The gate both records and succeeds, so a caller only has to return:
 *
 * <pre>
 *   if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
 *       return;
 *   }
 * </pre>
 *
 * <p>Thread-safe: GameTest batches run concurrently, and the counters behind this live in
 * {@link ScenarioReport} as atomics.
 */
public final class OptionalModGate {

    private OptionalModGate() {}

    /**
     * Skip the calling test when {@code modId} is absent.
     *
     * <p>Absent: records a skip against {@code modId}, calls {@code helper.succeed()} and
     * returns {@code true} so the caller returns immediately. Present: records the scenario
     * as executed and returns {@code false}.
     */
    public static boolean skipIfAbsent(GameTestHelper helper, String modId) {
        if (ModPresence.isLoaded(modId)) {
            ScenarioReport.executed(callerName());
            return false;
        }
        ScenarioReport.skipped(modId, callerName());
        helper.succeed();
        return true;
    }

    /**
     * The inverse gate, for the handful of tests that assert the <em>absent</em>-mod
     * behaviour and are meaningless once the mod is installed (for example
     * {@code ironAbsent_predicatesAreSafe}). Recorded as a skip against the same
     * {@code modId}: either way the run did not exercise the scenario, and that is what the
     * report exists to say.
     */
    public static boolean skipIfPresent(GameTestHelper helper, String modId) {
        if (!ModPresence.isLoaded(modId)) {
            ScenarioReport.executed(callerName());
            return false;
        }
        ScenarioReport.skipped(modId, callerName());
        helper.succeed();
        return true;
    }

    /**
     * The first frame outside this class, as {@code SimpleClassName#method}.
     *
     * <p>{@code GameTestHelper} keeps its {@code GameTestInfo} private with no accessor in
     * 1.21.1, so the test name cannot be asked for; the stack is the only honest source.
     * When a gate sits in a shared private helper (as in {@code AddonCompatGameTests}) the
     * name reported is that helper's, which is accurate about where the decision was made.
     * Names are for the DEBUG breakdown only - the counts are what CI reads.
     */
    private static String callerName() {
        return StackWalker.getInstance().walk(frames -> frames
            .filter(f -> !OptionalModGate.class.getName().equals(f.getClassName()))
            .findFirst()
            .map(f -> f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1)
                + "#" + f.getMethodName())
            .orElse("<unknown>"));
    }
}
