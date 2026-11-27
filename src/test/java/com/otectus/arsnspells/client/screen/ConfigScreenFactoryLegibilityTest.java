package com.otectus.arsnspells.client.screen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import com.otectus.arsnspells.TestPaths;

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
        return Files.readString(TestPaths.of(
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
        // 3.4.1 review CONFIG-01: the gate is the server's answer (canEdit), not the client's guess.
        int gate = src.indexOf("if (!canEdit()) return;");
        assertTrue(gate > 0 && gate < src.indexOf("step(setting);"),
            "the native button action must refuse changes in read-only mode");
        assertTrue(src.contains("control.active = canEdit();"),
            "disabled controls must also reject keyboard activation");
    }

    @Test
    void controls_useNativeWidgetHitboxesAndNarration() throws IOException {
        String src = source();
        assertTrue(src.contains("Button.builder(valueLabel(setting)"));
        assertTrue(src.contains("optionButtons.add(addRenderableWidget(control))"));
        assertTrue(src.contains(".createNarration("));
        assertFalse(src.contains("drawButtonChrome("), "native buttons own drawing and hit testing");
    }

    @Test
    void readOnlyMode_showsExplanatoryNote() throws IOException {
        String src = source();
        // 3.4.1 review CONFIG-01: localized, and it says who may edit instead of pointing at commands.
        assertTrue(src.contains("ars_n_spells.settings.read_only"),
            "clients that may not edit must see why the settings are read-only");
        assertTrue(src.contains("ars_n_spells.settings.scope"),
            "the screen must say the settings are world-global");
    }
}
