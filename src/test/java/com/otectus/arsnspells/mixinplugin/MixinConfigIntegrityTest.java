package com.otectus.arsnspells.mixinplugin;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integrity checks for the two mixin configs.
 *
 * <p>The split exists because a mixin targeting an <em>optional</em> dependency must not be
 * able to abort mod loading. {@code MixinProcessor} picks its error action with
 * {@code config.isRequired() ? ErrorAction.ERROR : ErrorAction.WARN}, so
 * {@code "required": false} is what turns a third-party conflict into a logged warning and a
 * skipped mixin instead of a crash that cascades through every mod in the pack. Ars Nouveau
 * mixins stay in the required config: Ars is a hard dependency, and a failure there means the
 * mod is genuinely broken and should say so loudly.
 */
class MixinConfigIntegrityTest {

    private static final Path CORE_CONFIG =
        TestPaths.of("src/main/resources/ars_n_spells.mixins.json");
    private static final Path COMPAT_CONFIG =
        TestPaths.of("src/main/resources/ars_n_spells.compat.mixins.json");
    private static final Path MIXIN_ROOT =
        TestPaths.of("src/main/java/com/otectus/arsnspells/mixin");
    private static final Path MODS_TOML =
        TestPaths.of("src/main/resources/META-INF/neoforge.mods.toml");

    /** Not a mixin: the config plugin, which both configs name in their "plugin" key. */
    private static final String PLUGIN_CLASS = "ArsNSpellsMixinPlugin";

    private static final Pattern LIST_ENTRY =
        Pattern.compile("\"([a-z_]+\\.Mixin[A-Za-z0-9]+|[a-z_]+\\.[A-Z][A-Za-z0-9]*)\"");

    /** Collect every entry in the "mixins", "client" and "server" arrays of a config. */
    private static Set<String> declaredMixins(Path config) throws IOException {
        String json = Files.readString(config);
        Set<String> found = new LinkedHashSet<>();
        for (String key : List.of("mixins", "client", "server")) {
            int start = json.indexOf('"' + key + '"');
            if (start < 0) {
                continue;
            }
            int open = json.indexOf('[', start);
            int close = json.indexOf(']', open);
            Matcher m = LIST_ENTRY.matcher(json.substring(open, close));
            while (m.find()) {
                found.add(m.group(1));
            }
        }
        return found;
    }

    /** Every @Mixin-annotated class on disk, as a "subpackage.SimpleName" entry. */
    private static Set<String> mixinClassesOnDisk() throws IOException {
        Set<String> found = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(MIXIN_ROOT)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String simpleName = file.getFileName().toString().replace(".java", "");
                if (simpleName.equals(PLUGIN_CLASS)) {
                    continue;
                }
                if (!Files.readString(file).contains("@Mixin")) {
                    continue;
                }
                found.add(MIXIN_ROOT.relativize(file.getParent()).toString().replace('\\', '.')
                    + '.' + simpleName);
            }
        }
        return found;
    }

    @Test
    void coreConfigIsRequiredAndCompatIsNot() throws IOException {
        assertTrue(Files.readString(CORE_CONFIG).contains("\"required\": true"),
            "Ars Nouveau is a hard dependency - a mixin failure there must stay fatal");
        assertTrue(Files.readString(COMPAT_CONFIG).contains("\"required\": false"),
            "Iron's Spellbooks is an optional dependency - a conflict there must warn and "
                + "skip, never abort mod loading for the whole pack");
    }

    @Test
    void defaultRequireMatchesEachConfigsPosture() throws IOException {
        assertTrue(Files.readString(CORE_CONFIG).contains("\"defaultRequire\": 1"),
            "an Ars injector that stops matching should fail, not silently no-op");
        assertTrue(Files.readString(COMPAT_CONFIG).contains("\"defaultRequire\": 0"),
            "an Iron's injector that stops matching must degrade, not fail the load");
    }

    @Test
    void everyMixinOnDiskIsRegisteredExactlyOnce() throws IOException {
        Set<String> core = declaredMixins(CORE_CONFIG);
        Set<String> compat = declaredMixins(COMPAT_CONFIG);

        List<String> duplicated = core.stream().filter(compat::contains).toList();
        assertTrue(duplicated.isEmpty(),
            "a mixin listed in both configs would be applied twice: " + duplicated);

        Set<String> registered = new TreeSet<>(core);
        registered.addAll(compat);

        // An unregistered class inside the declared mixin package is worse than dead code:
        // MixinProcessor throws IllegalClassLoadError if anything in a mixin package is
        // loaded without being a registered mixin.
        assertEquals(mixinClassesOnDisk(), registered,
            "every @Mixin class must be registered in exactly one config");
    }

    @Test
    void thirdPartyTargetingMixinsAreAllInTheCompatConfig() throws IOException {
        List<String> misplaced = declaredMixins(CORE_CONFIG).stream()
            .filter(entry -> entry.startsWith("irons."))
            .toList();

        assertTrue(misplaced.isEmpty(),
            "mixins targeting an optional dependency belong in the non-required compat "
                + "config, otherwise a third-party @Overwrite can abort mod loading: "
                + misplaced);
    }

    @Test
    void bothConfigsShareThePackageAndPlugin() throws IOException {
        String core = Files.readString(CORE_CONFIG);
        String compat = Files.readString(COMPAT_CONFIG);

        for (String shared : List.of(
                "\"package\": \"com.otectus.arsnspells.mixin\"",
                "\"plugin\": \"com.otectus.arsnspells.mixin.ArsNSpellsMixinPlugin\"",
                "\"compatibilityLevel\": \"JAVA_21\"")) {
            assertTrue(core.contains(shared) && compat.contains(shared),
                "both configs must declare " + shared + " - the gating plugin is shared");
        }
    }

    @Test
    void neitherConfigDeclaresARefmap() throws IOException {
        // NeoForge 1.21.1 runs on Mojang official mappings, so there is no reobfuscation
        // step and no refmap. Declaring one would make Mixin look for a mapping file that
        // is never produced. This is the 1.21.1 counterpart of the 1.20.1 line's refmap
        // integrity checks, not a dropped assertion.
        for (Path config : List.of(CORE_CONFIG, COMPAT_CONFIG)) {
            assertFalse(Files.readString(config).contains("\"refmap\""),
                config.getFileName() + " must not declare a refmap on a Mojang-mapped runtime");
        }
    }

    @Test
    void everyConfigIsDeclaredInTheModsToml() throws IOException {
        String toml = Files.readString(MODS_TOML);

        Set<String> onDisk = Set.of(CORE_CONFIG.getFileName().toString(),
            COMPAT_CONFIG.getFileName().toString());

        Set<String> registered = new LinkedHashSet<>();
        Matcher m = Pattern.compile("config\\s*=\\s*\"([^\"]+\\.json)\"").matcher(toml);
        while (m.find()) {
            registered.add(m.group(1));
        }
        assertEquals(onDisk, registered,
            "every mixin config must have a [[mixins]] entry in neoforge.mods.toml, or the "
                + "loader never applies the missing one - which on the compat config would "
                + "mean the whole Iron's integration silently does nothing");
    }

    @Test
    void theScrollCostMixinIsCoveredByARuntimeProbe() throws IOException {
        // The 1.20.1 line guarded this with a refmap assertion: MixinScrollItem targeted
        // `use` without remap = true, so in a reobfuscated jar the injects matched nothing,
        // require = 0 swallowed the miss, and scrolls cast completely free in every released
        // build. A Mojang-mapped runtime removes the reobfuscation, so there is no refmap to
        // assert against - but the failure mode (a silently unapplied inject) is not
        // mapping-specific, so the guard moves to a runtime probe instead of disappearing.
        String selfCheck = Files.readString(
            TestPaths.of("src/main/java/com/otectus/arsnspells/util/MixinSelfCheck.java"));
        assertTrue(selfCheck.contains("appendScrollUseProbe"),
            "MixinSelfCheck must probe Scroll.use, the injection whose silent failure made "
                + "scrolls free");
        assertTrue(selfCheck.contains("getDeclaredMethods"),
            "the probe must look at declared methods: Scroll.use is inherited from vanilla "
                + "Item, so a hierarchy-walking lookup reports OK even when the mixin never "
                + "applied");
    }
}
