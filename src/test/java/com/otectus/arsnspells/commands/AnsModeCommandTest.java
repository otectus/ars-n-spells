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
 * {@link com.otectus.arsnspells.client.screen.ConfigScreenFactoryGateTest} — bootstrap-free,
 * no Brigadier/Minecraft runtime required.
 */
class AnsModeCommandTest {

    @Test
    void command_hasOpGatedModeSetThatPersistsAndAppliesLive() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/commands/ArsNSpellsCommands.java"));
        assertTrue(src.contains("literal(\"set\")"),
            "/ans mode must have a 'set' subcommand");
        // 3.4.1 review CONFIG-01: operators, the console or the single-player owner, through one service
        // that persists, re-routes and reconciles (asserted against the service source below).
        assertTrue(src.contains(".requires(com.otectus.arsnspells.config.ConfigAuthority::mayChange)"),
            "/ans mode set must be gated by the shared authority predicate");
        assertTrue(src.contains("AnsSettingsService.apply("),
            "/ans mode set must apply through the shared settings service");
        String service = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/config/AnsSettingsService.java"));
        assertTrue(service.contains("value.set(entry.getValue())") && service.contains("BridgeManager.refreshMode()")
                && service.contains("ModeChangeCleanup.reconcileAll()"),
            "the service must persist the change, apply it live and reconcile online players");
    }

    @Test
    void configScreen_manaModeRowCyclesInsteadOfBeingAStub() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/client/screen/ConfigScreenFactory.java"));
        assertTrue(src.contains("case MODE ->") && src.contains("draft.put(setting.key(), modes[(index + 1) % modes.length]"),
            "the Mana Mode row must cycle the mode in the draft (it used to be a no-op setter)");
        assertTrue(src.contains("isCycle"),
            "the screen must support a cycling (non-boolean) row type");
        assertTrue(src.contains("new SettingsApplyPacket("),
            "Apply must send the change to the server, which applies it live");
        assertFalse(src.contains("Mode cycling handled separately"),
            "the dead-stub marker must be gone");
    }
}
