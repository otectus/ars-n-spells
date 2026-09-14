package com.otectus.arsnspells.client.screen;

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
 *   <li>Native button actions are gated on {@code canMutate} (boolean rows used to toggle
 *       ungated in multiplayer, silently mutating the client's SERVER-config mirror).</li>
 *   <li>Native widgets own rendering, hit testing and keyboard activation,
 *       so hitboxes always match the drawn buttons.</li>
 * </ul>
 */
class ConfigScreenFactoryLegibilityTest {

    private static String source() throws IOException {
        return Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/client/screen/ConfigScreenFactory.java"));
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
    void nativeControls_gatePointerAndKeyboardMutations() throws IOException {
        String src = source();
        int gate = src.indexOf("if (!canMutate) return;");
        assertTrue(gate > 0 && gate < src.indexOf("option.toggle()"),
            "the native button action must refuse changes in read-only mode");
        assertTrue(src.contains("control.active = canMutate;"),
            "disabled controls must also reject keyboard activation");
    }

    @Test
    void controls_useNativeWidgetHitboxesAndNarration() throws IOException {
        String src = source();
        assertTrue(src.contains("Button.builder(optionLabel(option)"));
        assertTrue(src.contains("optionButtons.add(addRenderableWidget(control))"));
        assertTrue(src.contains(".createNarration("));
        assertFalse(src.contains("drawButtonChrome("), "native buttons own drawing and hit testing");
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
