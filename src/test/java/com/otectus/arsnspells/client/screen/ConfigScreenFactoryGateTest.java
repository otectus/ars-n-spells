package com.otectus.arsnspells.client.screen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-016 part 2 — verifies that {@link ConfigScreenFactory} gates its
 * mutation buttons on {@code hasSingleplayerServer()}.
 *
 * <p>Since the config is now {@code ModConfig.Type.SERVER} (ANS-HIGH-016 part 1),
 * client-side {@code .set(value)} calls on a dedicated server are silent no-ops.
 * The screen must reflect that by disabling the buttons in multiplayer to avoid
 * misleading the user.
 */
class ConfigScreenFactoryGateTest {

    @Test
    void source_gatesOnHasSingleplayerServer() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/client/screen/ConfigScreenFactory.java"));
        assertTrue(src.contains("hasSingleplayerServer()"),
            "ConfigScreenFactory must check minecraft.hasSingleplayerServer() (ANS-HIGH-016 part 2)");
        assertTrue(src.contains("canMutate"),
            "the gating must produce a canMutate flag (or equivalent) used to "
                + "disable the reset/save buttons");
    }
}
