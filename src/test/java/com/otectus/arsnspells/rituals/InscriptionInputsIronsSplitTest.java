package com.otectus.arsnspells.rituals;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-002 - {@code InscriptionInputs} must not import Iron's API classes directly.
 *
 * <p>It is reached from {@code SpellUninscriptionRitual}, which is registered
 * unconditionally, so uninscription has to work on an Iron's-less server. A top-level import
 * would make the JVM verifier resolve the Iron's classes when {@code InscriptionInputs} loads,
 * regardless of any runtime {@code isLoaded()} check. The Iron's parsing path is isolated in
 * {@code IronsInscriptionReader}, reached only behind the gate.
 */
class InscriptionInputsIronsSplitTest {

    private static final Path INSCRIPTION_INPUTS =
        TestPaths.of("src/main/java/com/otectus/arsnspells/rituals/InscriptionInputs.java");
    private static final Path IRONS_READER =
        TestPaths.of("src/main/java/com/otectus/arsnspells/rituals/IronsInscriptionReader.java");

    @Test
    void inscriptionInputs_doesNotImportIronsClasses() throws IOException {
        String src = Files.readString(INSCRIPTION_INPUTS);
        assertFalse(src.contains("import io.redspace.ironsspellbooks"),
            "InscriptionInputs must not import any Iron's class directly (ANS-HIGH-002) - "
                + "the verifier resolves imports at classload, before any isLoaded() gate runs");
    }

    @Test
    void inscriptionInputs_delegatesToIronsInscriptionReader() throws IOException {
        String src = Files.readString(INSCRIPTION_INPUTS);
        assertTrue(src.contains("IronsInscriptionReader"),
            "InscriptionInputs.readSource must delegate to IronsInscriptionReader");
        assertTrue(src.contains("IronsCompat.isLoaded()"),
            "InscriptionInputs must gate the Iron's branch on IronsCompat.isLoaded() - the "
                + "call-site gate is what keeps the verifier away from IronsInscriptionReader "
                + "on Iron's-less installs");
    }

    @Test
    void ironsInscriptionReader_exists() {
        assertTrue(Files.exists(IRONS_READER),
            "IronsInscriptionReader.java must exist (ANS-HIGH-002 extraction target)");
    }
}
