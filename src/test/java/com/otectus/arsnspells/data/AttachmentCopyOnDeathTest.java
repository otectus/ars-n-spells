package com.otectus.arsnspells.data;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-008 - which per-player data survives death, and which does not.
 *
 * <p><b>Port divergence, deliberately pinned here.</b> The 1.20.1 line copied capabilities in a
 * {@code PlayerEvent.Clone} handler and needed {@code EventPriority.HIGHEST} so that a
 * third-party HIGHEST handler could not read the new player's caps before the copy ran and see
 * freshly-default state. NeoForge data attachments replace that with a declarative
 * {@code copyOnDeath()} flag applied by the platform itself, so there is no handler to
 * prioritise and no ordering race to lose. The invariant the ticket protects - long-term
 * progression survives respawn - is asserted directly instead.
 *
 * <p>Cooldowns deliberately do NOT copy: dying should not preserve a spell cooldown.
 */
class AttachmentCopyOnDeathTest {

    private static String source() throws IOException {
        return Files.readString(
            TestPaths.of("src/main/java/com/otectus/arsnspells/data/AttachmentTypes.java"));
    }

    private static String blockFor(String src, String name) {
        int idx = src.indexOf("AttachmentType<" + name);
        assertTrue(idx > 0, name + " attachment must be declared");
        int end = src.indexOf(");", idx);
        return src.substring(idx, end < 0 ? src.length() : end);
    }

    @Test
    void affinityAndProgressionSurviveDeath() throws IOException {
        String src = source();
        assertTrue(blockFor(src, "AffinityData").contains("copyOnDeath"),
            "school affinity is long-term progression and must survive respawn (ANS-HIGH-008)");
        assertTrue(blockFor(src, "ProgressionData").contains("copyOnDeath"),
            "cast-count progression must survive respawn (ANS-HIGH-008)");
    }

    @Test
    void cooldownsDoNotSurviveDeath() throws IOException {
        assertTrue(!blockFor(source(), "CooldownData").contains("copyOnDeath"),
            "cooldowns must NOT copy on death - dying is not a way to keep a spell on "
                + "cooldown, and carrying one across a respawn is the surprising behaviour");
    }
}
