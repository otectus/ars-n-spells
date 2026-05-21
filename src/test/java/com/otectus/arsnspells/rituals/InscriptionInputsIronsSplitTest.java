package com.otectus.arsnspells.rituals;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-002 — verifies that {@code InscriptionInputs} no longer imports the
 * three Iron's API classes directly. The Iron's parsing path is now isolated to
 * the new {@code IronsInscriptionReader} class which is reached only via
 * {@code IronsCompat.isLoaded()} gate.
 *
 * <p>This is critical because {@code InscriptionInputs} is invoked by
 * {@code SpellUninscriptionRitual} which is registered unconditionally —
 * uninscription must work even on Iron's-less servers.
 */
class InscriptionInputsIronsSplitTest {

    private static final Path INSCRIPTION_INPUTS = Paths.get(
        "src/main/java/com/otectus/arsnspells/rituals/InscriptionInputs.java");
    private static final Path IRONS_READER = Paths.get(
        "src/main/java/com/otectus/arsnspells/rituals/IronsInscriptionReader.java");

    @Test
    void inscriptionInputs_doesNotImportIronsClasses() throws IOException {
        String src = Files.readString(INSCRIPTION_INPUTS);
        // The three Iron's imports that used to live here are now in IronsInscriptionReader.
        assertFalse(src.contains("import io.redspace.ironsspellbooks.api.spells.AbstractSpell"),
            "InscriptionInputs must not import AbstractSpell directly");
        assertFalse(src.contains("import io.redspace.ironsspellbooks.api.spells.ISpellContainer"),
            "InscriptionInputs must not import ISpellContainer directly");
        assertFalse(src.contains("import io.redspace.ironsspellbooks.api.spells.SpellData"),
            "InscriptionInputs must not import SpellData directly");
    }

    @Test
    void inscriptionInputs_delegatesToIronsInscriptionReader() throws IOException {
        String src = Files.readString(INSCRIPTION_INPUTS);
        assertTrue(src.contains("IronsInscriptionReader.tryRead"),
            "InscriptionInputs.readSource must delegate to IronsInscriptionReader.tryRead");
        assertTrue(src.contains("IronsCompat.isLoaded()"),
            "InscriptionInputs must gate the Iron's branch on IronsCompat.isLoaded() — "
                + "the call-site gate is what keeps the JVM verifier away from "
                + "IronsInscriptionReader on Iron's-less installs");
    }

    @Test
    void ironsInscriptionReader_exists() {
        assertTrue(Files.exists(IRONS_READER),
            "IronsInscriptionReader.java must exist (ANS-HIGH-002 extraction target)");
    }
}
