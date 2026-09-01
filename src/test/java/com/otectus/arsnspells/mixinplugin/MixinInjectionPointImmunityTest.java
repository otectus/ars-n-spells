package com.otectus.arsnspells.mixinplugin;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the invariant whose violation crashed a user's pack and took twelve mods down with it.
 *
 * <p>Mixin's {@code Injector.findTargetNodes} refuses to inject into a method another mod has
 * {@code @Overwrite}-merged at equal-or-higher priority:
 *
 * <pre>
 * if (injectorTarget.isMerged()
 *         &amp;&amp; !mixin.getClassName().equals(injectorTarget.getMergedBy())
 *         &amp;&amp; !injectionPoint.checkPriority(injectorTarget.getMergedPriority(), mixin.getPriority())) {
 *     throw new InvalidInjectionException(...);
 * }
 * </pre>
 *
 * <p>That throw happens during PREPARE, before the {@code require} count is consulted, so
 * {@code require = 0} does not soften it - the exception propagates and mod loading aborts for
 * every mod in the chain.
 *
 * <p>{@code MethodHead}, {@code BeforeReturn} and {@code BeforeFinalReturn} each override
 * {@code InjectionPoint.checkPriority} to return {@code true}, so {@code HEAD}, {@code RETURN}
 * and {@code TAIL} are structurally immune. Every other injection point - {@code INVOKE},
 * {@code FIELD}, {@code CONSTANT}, {@code NEW}, {@code JUMP} - is not.
 *
 * <p>So: any new instruction-level injection point in this mod must be a deliberate, reviewed
 * decision. Adding one fails this test until it is added to
 * {@link #INSTRUCTION_LEVEL_ALLOWLIST}, and anything on that allowlist must live in the
 * non-required mixin config so the failure degrades instead of aborting the load.
 *
 * <p>This class lives in {@code mixinplugin} rather than {@code mixin}: the Mixin transformer
 * refuses to load a class that sits inside a declared mixin package.
 */
class MixinInjectionPointImmunityTest {

    private static final Path MIXIN_ROOT =
        TestPaths.of("src/main/java/com/otectus/arsnspells/mixin");

    /** Injection points that cannot trigger the merged-priority rejection. */
    private static final Set<String> IMMUNE = Set.of("HEAD", "RETURN", "TAIL");

    /**
     * Files permitted to use an instruction-level injection point.
     *
     * <p>Empty, and it should stay that way. The 1.20.1 line allowlisted exactly one file -
     * Covenant of the Seven's HUD overlay, whose {@code void render} target had no HEAD/RETURN
     * formulation of "replace the fill-width divisor". That mixin is not part of this build.
     */
    private static final Set<String> INSTRUCTION_LEVEL_ALLOWLIST = Set.of();

    /** Matches both {@code @At("HEAD")} and {@code @At(value = "INVOKE", ...)}. */
    private static final Pattern AT_SIMPLE = Pattern.compile("@At\\s*\\(\\s*\"([A-Z_]+)\"");
    private static final Pattern AT_VERBOSE =
        Pattern.compile("@At\\s*\\(\\s*value\\s*=\\s*\"([A-Z_]+)\"");

    private static List<Path> mixinSources() throws IOException {
        try (Stream<Path> paths = Files.walk(MIXIN_ROOT)) {
            return paths.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    /**
     * Strip block and line comments so the assertions below judge code, not prose. These files
     * deliberately discuss {@code @Redirect} in their javadoc to explain why it is no longer
     * used, and a naive substring match would flag that.
     */
    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/[*].*?[*]/", "").replaceAll("(?m)//.*$", "");
    }

    @Test
    void everyInjectionPointIsPriorityImmune() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path file : mixinSources()) {
            String name = file.getFileName().toString();
            if (INSTRUCTION_LEVEL_ALLOWLIST.contains(name)) {
                continue;
            }
            String source = codeOnly(Files.readString(file));

            for (Pattern pattern : List.of(AT_SIMPLE, AT_VERBOSE)) {
                Matcher m = pattern.matcher(source);
                while (m.find()) {
                    String point = m.group(1);
                    if (!IMMUNE.contains(point)) {
                        violations.add(name + " uses @At(\"" + point + "\")");
                    }
                }
            }

            // @ModifyConstant implies a BeforeConstant injection point, which has no
            // checkPriority override and is therefore just as exposed as @At("CONSTANT").
            if (source.contains("@ModifyConstant")) {
                violations.add(name + " uses @ModifyConstant (instruction-level)");
            }
        }

        assertTrue(violations.isEmpty(),
            "Instruction-level injection points are rejected outright when another mod "
                + "@Overwrite-merges the target method, and require = 0 does NOT prevent it "
                + "- that is what took twelve mods down at load. Use HEAD/RETURN/TAIL, or "
                + "add the file to INSTRUCTION_LEVEL_ALLOWLIST and put it in the "
                + "non-required compat config. Offenders: " + violations);
    }

    @Test
    void allowlistedFilesLiveInTheNonRequiredConfig() throws IOException {
        String compat = Files.readString(
            TestPaths.of("src/main/resources/ars_n_spells.compat.mixins.json"));
        assertTrue(compat.contains("\"required\": false"),
            "the compat config must stay non-required, or the allowlist loses its mitigation");

        for (String fileName : INSTRUCTION_LEVEL_ALLOWLIST) {
            String simpleName = fileName.substring(0, fileName.length() - ".java".length());
            assertTrue(compat.contains(simpleName),
                simpleName + " uses an instruction-level injection point, so it must be "
                    + "listed in ars_n_spells.compat.mixins.json where a conflict only warns");
        }
    }

    @Test
    void castValidationMixinKeepsNoRedirect() throws IOException {
        // The exact regression: a @Redirect on INVOKE MagicData.getMana() inside
        // AbstractSpell.canBeCastedBy, which One Mana Bar's @Overwrite made fatal.
        String source = codeOnly(Files.readString(
            MIXIN_ROOT.resolve("irons/MixinIronsCastValidation.java")));

        assertFalse(source.contains("@Redirect("),
            "MixinIronsCastValidation must not reintroduce a @Redirect - it is the "
                + "injection that crashed the reported pack");
        assertTrue(source.contains("@At(\"HEAD\")") && source.contains("@At(\"RETURN\")"),
            "the cast-gate scope needs both its HEAD producer and its RETURN consumer");
    }

    @Test
    void castValidationMixinDeclaresNoState() throws IOException {
        // Mixin merges mixin fields into the target class. An un-@Unique name can collide
        // with AbstractSpell's own members or with another mod's mixin, so this class keeps
        // its logger and throttle map on CastValidationScope instead.
        String source = codeOnly(Files.readString(
            MIXIN_ROOT.resolve("irons/MixinIronsCastValidation.java")));
        String body = source.substring(source.indexOf("public abstract class"));

        assertFalse(body.contains("private static final Logger"),
            "state declared on a mixin is merged into AbstractSpell - keep it on "
                + "CastValidationScope");
        assertFalse(body.contains("ConcurrentHashMap"),
            "state declared on a mixin is merged into AbstractSpell - keep it on "
                + "CastValidationScope");
    }

    @Test
    void magicDataManaMixinKeepsTheCastGateReturnHook() throws IOException {
        // The second, independent layer: it applies the conversion to whatever the real
        // getMana body returned, including a body another mod has overwritten. Losing it
        // leaves the whole rewrite resting on the HEAD hook alone.
        String source = codeOnly(Files.readString(
            MIXIN_ROOT.resolve("irons/MixinIronsMagicDataMana.java")));
        assertTrue(source.contains("arsnspells$scaleManaForCastGate"),
            "MixinIronsMagicDataMana must keep the getMana RETURN hook that applies the "
                + "cast-gate scope to the real method's own return value");
        assertTrue(source.contains("CastValidationScope.isActive"),
            "the RETURN hook must skip the setReturnValue when no scope is live");
    }
}
