package com.otectus.arsnspells.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS 3.0.1 legibility pass — verifies the config screen fixes for the
 * "blurry / frosted glass" report:
 *
 * <ul>
 *   <li>The screen paints its own opaque background instead of the vanilla
 *       translucent dim ({@code renderBackground}), which client blur mods hook.</li>
 *   <li>Row clicks are gated on {@code canMutate} (boolean rows used to toggle
 *       ungated in multiplayer, silently mutating the client's SERVER-config mirror).</li>
 *   <li>Render and click share the same control geometry ({@code buttonRect}),
 *       so hitboxes always match the drawn buttons.</li>
 * </ul>
 */
class ConfigScreenFactoryLegibilityTest {

    private static String source() throws IOException {
        return Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/config/ConfigScreenFactory.java"));
    }

    @Test
    void render_paintsOwnedOpaqueBackground() throws IOException {
        String src = source();
        assertTrue(src.contains("graphics.fill(0, 0, this.width, this.height"),
            "render must paint a deterministic full-screen fill");
        assertFalse(src.contains("renderBackground(graphics)"),
            "render must not call renderBackground — blur mods hook it (frosted-glass report)");
    }

    @Test
    void mouseClicked_gatesAllRowsOnCanMutate() throws IOException {
        String src = source();
        int clickIdx = src.indexOf("public boolean mouseClicked");
        assertTrue(clickIdx > 0, "mouseClicked must exist");
        int gateIdx = src.indexOf("canMutate", clickIdx);
        int toggleIdx = src.indexOf("option.toggle()", clickIdx);
        assertTrue(gateIdx > 0 && toggleIdx > gateIdx,
            "mouseClicked must check canMutate before any row toggles "
                + "(boolean rows used to mutate ungated in multiplayer)");
    }

    @Test
    void hitboxes_shareGeometryWithRender() throws IOException {
        String src = source();
        int renderIdx = src.indexOf("public void render");
        int clickIdx = src.indexOf("public boolean mouseClicked");
        // renderRow is invoked from render and uses buttonRect internally;
        // mouseClicked must hit-test the same rect.
        assertTrue(src.indexOf("buttonRect(", src.indexOf("private void renderRow")) > 0,
            "renderRow must draw controls at buttonRect");
        assertTrue(src.indexOf("buttonRect(", clickIdx) > 0,
            "mouseClicked must hit-test against buttonRect");
        assertTrue(renderIdx > 0 && clickIdx > 0);
    }

    @Test
    void readOnlyMode_showsExplanatoryNote() throws IOException {
        String src = source();
        assertTrue(src.contains("Read-only: server-managed config."),
            "multiplayer clients must see a note that the config is server-managed");
        assertTrue(src.contains("/ans commands"),
            "the note must point at the /ans command path");
    }
}
