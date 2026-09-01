package com.otectus.arsnspells.commands;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the wiring that makes {@code mana_unification_mode} changeable in game: the
 * {@code /ans mode set} command and the config screen's Mana Mode cycle row.
 *
 * <p>Source-level assertions, bootstrap-free - no Brigadier or Minecraft runtime required.
 */
class AnsModeCommandTest {

    private static String read(String path) throws IOException {
        return Files.readString(TestPaths.of(path));
    }

    @Test
    void command_hasOpGatedModeSetThatPersistsAndAppliesLive() throws IOException {
        String src = read("src/main/java/com/otectus/arsnspells/commands/ArsNSpellsCommands.java");
        assertTrue(src.contains("literal(\"set\")"), "/ans mode must have a 'set' subcommand");
        assertTrue(src.contains("hasPermission(2)"),
            "/ans mode set must be op-gated (permission level 2) - it rewrites a "
                + "server-authoritative gameplay key");
        assertTrue(src.contains("MANA_UNIFICATION_MODE.set"),
            "/ans mode set must persist the new mode to config");
        assertTrue(src.contains("BridgeManager.refreshMode"),
            "/ans mode set must apply the change live via BridgeManager.refreshMode(), or the "
                + "config and the running bridges disagree until restart");
    }

    @Test
    void configScreen_manaModeRowCyclesInsteadOfBeingAStub() throws IOException {
        String src = read("src/main/java/com/otectus/arsnspells/client/screen/ConfigScreenFactory.java");
        assertTrue(src.contains("MANA_UNIFICATION_MODE.set"),
            "the Mana Mode row must write the mode (it used to be a no-op setter)");
        assertTrue(src.contains("isCycle"),
            "the screen must support a cycling (non-boolean) row type");
        assertTrue(src.contains("refreshMode"),
            "saving the screen must apply the mode change live");
        assertFalse(src.contains("Mode cycling handled separately"),
            "the dead-stub marker must be gone");
    }
}
