package com.otectus.arsnspells.client.screen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-016 part 2, revised by the 3.4.1 review (CONFIG-01): the screen never writes the
 * client's copy of the SERVER config, which on a dedicated server is a silent no-op and on a
 * single-player host bypasses the server thread. It edits a draft and sends it to the server, which
 * decides who may change settings; the screen enables its controls from the server's answer.
 */
class ConfigScreenFactoryGateTest {

    @Test
    void source_editsADraftTheServerAuthorizes() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/client/screen/ConfigScreenFactory.java"));
        assertTrue(src.contains("settings.canEdit()"),
            "the mutation gate must be the server's canEdit answer");
        assertTrue(src.contains("new SettingsApplyPacket("),
            "Apply must send the draft to the server");
        assertTrue(!src.contains("AnsConfig.") || !src.contains(".set("),
            "the screen must not write AnsConfig values directly");
    }
}
