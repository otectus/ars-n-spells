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
    void command_hasAuthorizedModeSetThatPersistsAndAppliesLive() throws IOException {
        // 3.4.1 review CONFIG-01: operators, the console or the single-player owner, through one service
        // that persists, re-routes and reconciles (asserted against the service source below).
        String src = read("src/main/java/com/otectus/arsnspells/commands/ArsNSpellsCommands.java");
        assertTrue(src.contains("literal(\"set\")"), "/ans mode must have a 'set' subcommand");
        assertTrue(src.contains(".requires(com.otectus.arsnspells.config.ConfigAuthority::mayChange)"),
            "/ans mode set must be gated by the shared authority predicate");
        assertTrue(src.contains("AnsSettingsService.apply("), "/ans mode set must apply through the shared settings service");
        String service = read("src/main/java/com/otectus/arsnspells/config/AnsSettingsService.java");
        assertTrue(service.contains("SPEC.save()") && service.contains("BridgeManager.refreshMode()"),
            "the service must persist the change and apply it live (refreshMode reconciles online players on NeoForge)");
    }

    @Test
    void configScreen_manaModeRowCyclesInsteadOfBeingAStub() throws IOException {
        String src = read("src/main/java/com/otectus/arsnspells/client/screen/ConfigScreenFactory.java");
        assertTrue(src.contains("case MODE ->") && src.contains("draft.put(setting.key(), modes[(index + 1) % modes.length]"),
            "the Mana Mode row must cycle the mode in the draft (it used to be a no-op setter)");
        assertTrue(src.contains("isCycle"), "the screen must support a cycling (non-boolean) row type");
        assertTrue(src.contains("new SettingsApplyPayload("), "Apply must send the change to the server, which applies it live");
        assertFalse(src.contains("Mode cycling handled separately"), "the dead-stub marker must be gone");
    }
}
