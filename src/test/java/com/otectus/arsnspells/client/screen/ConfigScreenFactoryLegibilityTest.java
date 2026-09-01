package com.otectus.arsnspells.client.screen;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The config screen has to stay readable and honest.
 *
 * <p>Two properties, both of which have been got wrong before. It paints its own opaque
 * background rather than relying on the vanilla dim, so a shader or blur mod cannot leave the
 * rows unreadable. And because the config is server-authoritative, every mutation path is
 * gated on {@code canMutate} with a visible explanation - a screen that silently ignores
 * clicks is worse than one that says why.
 */
class ConfigScreenFactoryLegibilityTest {

    private static String source() throws IOException {
        return Files.readString(TestPaths.of(
            "src/main/java/com/otectus/arsnspells/client/screen/ConfigScreenFactory.java"));
    }

    @Test
    void render_paintsOwnedOpaqueBackground() throws IOException {
        assertTrue(source().contains("graphics.fill(0, 0, this.width, this.height"),
            "the screen must paint its own opaque background; a blur/shader mod cannot frost "
                + "an owned fill, but it can render the vanilla dim unreadable");
    }

    @Test
    void mouseClicked_gatesEveryRowOnCanMutate() throws IOException {
        String src = source();
        int clickIdx = src.indexOf("public boolean mouseClicked");
        assertTrue(clickIdx > 0, "mouseClicked must exist");
        int gateIdx = src.indexOf("canMutate", clickIdx);
        assertTrue(gateIdx > clickIdx,
            "mouseClicked must consult canMutate before mutating any row - on a dedicated "
                + "server the set() calls are silent no-ops");
    }

    @Test
    void hitboxesShareGeometryWithRender() throws IOException {
        String src = source();
        assertTrue(src.contains("buttonRect("),
            "render and hit-testing must derive their geometry from one helper, or the "
                + "clickable area drifts from the drawn button");
    }

    @Test
    void readOnlyModeExplainsItself() throws IOException {
        String src = source();
        assertTrue(src.toLowerCase().contains("read-only"),
            "a screen that cannot be edited must say so");
        assertTrue(src.contains("/ans"),
            "and must point at the command that CAN change these values on a server");
    }
}
