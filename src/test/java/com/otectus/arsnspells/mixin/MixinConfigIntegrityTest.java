package com.otectus.arsnspells.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integrity checks for the two mixin configs and the refmap they share.
 *
 * <p>The split exists because a mixin that targets an <em>optional</em> dependency must
 * not be able to abort mod loading. {@code MixinProcessor} picks its error action with
 * {@code config.isRequired() ? ErrorAction.ERROR : ErrorAction.WARN}, so
 * {@code "required": false} is what turns a third-party conflict into a logged warning
 * and a skipped mixin instead of a crash that cascades through every mod in the pack.
 * Ars Nouveau mixins stay in the required config: Ars is a hard dependency, and a
 * failure there means the mod is genuinely broken and should say so loudly.
 */
class MixinConfigIntegrityTest {

    private static final Path CORE_CONFIG = Paths.get("src/main/resources/ars_n_spells.mixins.json");
    private static final Path COMPAT_CONFIG = Paths.get("src/main/resources/ars_n_spells.compat.mixins.json");
    private static final Path MIXIN_ROOT = Paths.get("src/main/java/com/otectus/arsnspells/mixin");
    private static final Path REFMAP = Paths.get("build/tmp/compileJava/ars_n_spells.refmap.json");

    /** Not a mixin: the config plugin, which both configs name in their "plugin" key. */
    private static final String PLUGIN_CLASS = "ArsNSpellsMixinPlugin";

    private static final Pattern LIST_ENTRY = Pattern.compile("\"([a-z_]+\\.Mixin[A-Za-z0-9]+|[a-z_]+\\.[A-Z][A-Za-z0-9]*)\"");

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
            "Iron's/Covenant are optional dependencies - a conflict there must warn and "
                + "skip, never abort mod loading for the whole pack");
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

        // An unregistered class inside the declared mixin package is worse than dead
        // code: MixinProcessor throws IllegalClassLoadError if anything in a mixin
        // package is loaded without being a registered mixin.
        assertEquals(mixinClassesOnDisk(), registered,
            "every @Mixin class must be registered in exactly one config");
    }

    @Test
    void thirdPartyTargetingMixinsAreAllInTheCompatConfig() throws IOException {
        Set<String> core = declaredMixins(CORE_CONFIG);
        List<String> misplaced = core.stream()
            .filter(entry -> entry.startsWith("irons.")
                || entry.startsWith("sanctified.")
                || entry.startsWith("covenant."))
            .toList();

        assertTrue(misplaced.isEmpty(),
            "mixins targeting an optional dependency belong in the non-required compat "
                + "config, otherwise a third-party @Overwrite can abort mod loading: " + misplaced);
    }

    @Test
    void bothConfigsShareOneRefmapAndPlugin() throws IOException {
        String core = Files.readString(CORE_CONFIG);
        String compat = Files.readString(COMPAT_CONFIG);

        for (String shared : List.of(
                "\"refmap\": \"ars_n_spells.refmap.json\"",
                "\"package\": \"com.otectus.arsnspells.mixin\"",
                "\"plugin\": \"com.otectus.arsnspells.mixin.ArsNSpellsMixinPlugin\"",
                "\"compatibilityLevel\": \"JAVA_17\"")) {
            assertTrue(core.contains(shared) && compat.contains(shared),
                "both configs must declare " + shared
                    + " - MixinGradle emits one refmap per source set, and the gating "
                    + "plugin is shared");
        }
    }

    @Test
    void buildGradleAndManifestAgreeWithTheConfigsOnDisk() throws IOException {
        String buildGradle = Files.readString(Paths.get("build.gradle"));

        Set<String> onDisk = Set.of(CORE_CONFIG.getFileName().toString(),
            COMPAT_CONFIG.getFileName().toString());

        Set<String> registered = new LinkedHashSet<>();
        Matcher m = Pattern.compile("config\\s+\"([^\"]+\\.json)\"").matcher(buildGradle);
        while (m.find()) {
            registered.add(m.group(1));
        }
        assertEquals(onDisk, registered,
            "every mixin config must be registered in the build.gradle mixin block");

        Matcher manifest = Pattern.compile("\"MixinConfigs\"\\s*:\\s*\"([^\"]+)\"").matcher(buildGradle);
        assertTrue(manifest.find(), "the jar manifest must declare MixinConfigs");
        assertEquals(onDisk, Set.of(manifest.group(1).split(",")),
            "the MixinConfigs manifest attribute must list every config, or FML never "
                + "loads the missing one");
    }

    @Test
    void refmapCoversEveryRemappedInjector() throws IOException {
        // MixinScrollItem targeted `use` with remap inherited as false. In a shipped jar
        // that method is reobfuscated to m_7203_, so the injects silently found nothing
        // and require = 0 swallowed the miss - scrolls cast free of mana, LP and aura in
        // every released build. The refmap is where that bug is visible, so assert it.
        assertTrue(Files.exists(REFMAP),
            "refmap missing - run ./gradlew compileJava first (test depends on it)");
        String refmap = Files.readString(REFMAP);

        assertTrue(refmap.contains("MixinScrollItem"),
            "MixinScrollItem must have a refmap entry, or its injects will not resolve "
                + "against the reobfuscated Scroll class in a shipped jar");
        assertTrue(refmap.contains("Lio/redspace/ironsspellbooks/item/Scroll;m_7203_("),
            "MixinScrollItem.use must map to Scroll.m_7203_ - without remap = true the "
                + "annotation processor emits no mapping and scroll costs silently stop "
                + "being enforced");
        assertTrue(refmap.contains("InscriptionTableMenu;m_6366_("),
            "MixinInscriptionTableMenu.clickMenuButton must still map to m_6366_");
    }

    @Test
    void everyRemapTrueInjectorHasARefmapEntry() throws IOException {
        // Generalises the check above: any injector that opts into remapping on a
        // remap = false mixin must actually produce a mapping.
        assertTrue(Files.exists(REFMAP), "refmap missing - run ./gradlew compileJava first");
        String refmap = Files.readString(REFMAP);

        List<String> missing = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(MIXIN_ROOT)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (!source.contains("remap = true")) {
                    continue;
                }
                String simpleName = file.getFileName().toString().replace(".java", "");
                if (!refmap.contains(simpleName)) {
                    missing.add(simpleName);
                }
            }
        }
        assertTrue(missing.isEmpty(),
            "these mixins declare remap = true but produced no refmap entry, so their "
                + "injects will not resolve in a production jar: " + missing);
    }
}
