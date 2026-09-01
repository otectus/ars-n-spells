package com.otectus.arsnspells;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves paths against the project root for the structural tests — the ones that read the
 * resource tree or the registrar sources to assert invariants the compiler cannot check.
 *
 * <p>They cannot use bare relative paths. ModDevGradle's {@code unitTest} harness runs the
 * suite from {@code build/minecraft-junit} so the Minecraft bootstrap has a game directory, so
 * {@code Paths.get("src/main/...")} resolves under the build directory and every such test
 * fails with {@code NoSuchFileException}. {@code build.gradle} passes the real project
 * directory as {@code ans.projectDir}; this resolves against it, falling back to the working
 * directory so the tests still work if they are ever run outside Gradle.
 */
public final class TestPaths {

    private static final Path ROOT = Paths.get(
        System.getProperty("ans.projectDir", System.getProperty("user.dir", ".")));

    private TestPaths() {}

    /** Project-root-relative path, e.g. {@code of("src/main/resources/...")}. */
    public static Path of(String first, String... more) {
        return ROOT.resolve(Paths.get(first, more));
    }

    /** The project root itself. */
    public static Path root() {
        return ROOT;
    }
}
