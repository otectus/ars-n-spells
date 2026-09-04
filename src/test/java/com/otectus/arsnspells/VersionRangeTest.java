package com.otectus.arsnspells;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the optional-dependency ranges in {@code mods.toml} actually admit the versions they
 * were written for, using the same maven-artifact implementation FML resolves them with.
 *
 * <p>The range that needs this most is Covenant's: {@code [2.2.6,)} against a jar that declares
 * {@code 2.2.6-hotfix}. Maven sorts a qualifier <em>below</em> its bare release for most
 * qualifier words, so the inclusion is a property of this specific string, not an obvious one —
 * a typo in either half turns the soft dependency into a version-mismatch screen and nothing
 * else in the build would catch it.
 */
class VersionRangeTest {

    private static void assertAdmits(String range, String declared)
        throws InvalidVersionSpecificationException {
        assertTrue(VersionRange.createFromVersionSpec(range)
                .containsVersion(new DefaultArtifactVersion(declared)),
            "mods.toml range " + range + " must admit the shipped version " + declared);
    }

    @Test
    void arsElementalRange_admitsShippedVersion() throws Exception {
        assertAdmits("[0.6.8.0,)", "0.6.8.0");
    }

    @Test
    void arsZeroRange_admitsShippedVersion() throws Exception {
        assertAdmits("[2.0.2,)", "2.0.2");
    }

    @Test
    void covenantRange_admitsTheHotfixQualifier() throws Exception {
        assertAdmits("[2.2.6,)", "2.2.6-hotfix");
    }
}
