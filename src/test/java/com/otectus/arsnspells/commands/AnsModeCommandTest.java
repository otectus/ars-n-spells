package com.otectus.arsnspells.commands;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS 2.0.1 — pins the wiring that makes {@code mana_unification_mode} changeable
 * in-game: the {@code /ans mode set} command and the config-screen "Mana Mode" cycle
 * row. Source-level assertions in the style of
 * {@link com.otectus.arsnspells.config.ConfigScreenFactoryGateTest} — bootstrap-free,
 * no Brigadier/Minecraft runtime required.
 */
class AnsModeCommandTest {

    @Test
    void command_hasOpGatedModeSetThatPersistsAndAppliesLive() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/commands/ArsNSpellsCommands.java"));
        assertTrue(src.contains("literal(\"set\")"),
            "/ans mode must have a 'set' subcommand");
        assertTrue(src.contains("hasPermission(2)"),
            "/ans mode set must be op-gated (permission level 2)");
        assertTrue(src.contains("MANA_UNIFICATION_MODE.set"),
            "/ans mode set must persist the new mode to config");
        assertTrue(src.contains("BridgeManager.refreshMode"),
            "/ans mode set must apply the change live via BridgeManager.refreshMode()");
    }

    @Test
    void configScreen_manaModeRowCyclesInsteadOfBeingAStub() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/config/ConfigScreenFactory.java"));
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
