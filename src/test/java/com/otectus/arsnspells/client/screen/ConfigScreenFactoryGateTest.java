package com.otectus.arsnspells.client.screen;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-016 part 2 - the config screen must gate its mutation buttons on
 * {@code hasSingleplayerServer()}.
 *
 * <p>The config is {@code ModConfig.Type.SERVER} (part 1), so a client-side {@code .set(value)}
 * against a dedicated server is a silent no-op. The screen has to reflect that by disabling the
 * buttons in multiplayer rather than letting the player think they changed something.
 */
class ConfigScreenFactoryGateTest {

    @Test
    void source_editsADraftTheServerAuthorizes() throws IOException {
        // 3.4.1 review CONFIG-01: the screen never writes the client's copy of the SERVER config; it
        // sends a draft the server authorizes, and enables its controls from the server's answer.
        String src = Files.readString(TestPaths.of(
            "src/main/java/com/otectus/arsnspells/client/screen/ConfigScreenFactory.java"));
        assertTrue(src.contains("settings.canEdit()"), "the mutation gate must be the server's canEdit answer");
        assertTrue(src.contains("new SettingsApplyPayload("), "Apply must send the draft to the server");
        assertTrue(!src.contains("AnsConfig.") || !src.contains(".set("), "the screen must not write AnsConfig values directly");
    }
}
