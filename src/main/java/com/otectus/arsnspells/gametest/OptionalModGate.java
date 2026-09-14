package com.otectus.arsnspells.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;

/**
 * The single door every optional-mod GameTest goes through, so a skip is recorded as a skip
 * instead of disappearing into the pass count (audit V26).
 *
 * <p>Usage is always the first statement of the test:
 *
 * <pre>
 *   if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
 *       return;
 *   }
 * </pre>
 *
 * <p>GameTest still has no skip verdict, so an absent mod must succeed the test — but
 * {@link ScenarioReport} now knows it did not run, and that is what CI asserts on.
 *
 * <p>The test name is read off the call stack rather than off {@code GameTestHelper}, whose
 * {@code testInfo} field is private with no accessor in 1.20.1.
 */
public final class OptionalModGate {

    private OptionalModGate() {
    }

    /**
     * Succeed and record a skip when {@code modId} is absent; record an execution otherwise.
     *
     * @return true when the caller must return immediately without asserting anything
     */
    public static boolean skipIfAbsent(GameTestHelper helper, String modId) {
        String testName = callerTestName();
        if (ModList.get().isLoaded(modId)) {
            ScenarioReport.executed(testName);
            return false;
        }
        ScenarioReport.skipped(modId, testName);
        helper.succeed();
        return true;
    }

    /** The first frame outside this class, i.e. the {@code @GameTest} method that gated itself. */
    private static String callerTestName() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
            .walk(frames -> frames
                .filter(frame -> {
                    try {
                        return frame.getDeclaringClass().getDeclaredMethod(frame.getMethodName(), GameTestHelper.class)
                            .isAnnotationPresent(net.minecraft.gametest.framework.GameTest.class);
                    } catch (ReflectiveOperationException ignored) {
                        return false;
                    }
                })
                .findFirst()
                .map(frame -> {
                    String type = frame.getClassName();
                    return type.substring(type.lastIndexOf('.') + 1) + "#" + frame.getMethodName();
                })
                .orElse("<unknown test>"));
    }
}
