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
    void source_gatesOnHasSingleplayerServer() throws IOException {
        String src = Files.readString(TestPaths.of(
            "src/main/java/com/otectus/arsnspells/client/screen/ConfigScreenFactory.java"));
        assertTrue(src.contains("hasSingleplayerServer()"),
            "ConfigScreenFactory must check minecraft.hasSingleplayerServer() "
                + "(ANS-HIGH-016 part 2)");
        assertTrue(src.contains("canMutate"),
            "the gating must produce a canMutate flag (or equivalent) used to disable the "
                + "reset/save buttons");
    }
}
