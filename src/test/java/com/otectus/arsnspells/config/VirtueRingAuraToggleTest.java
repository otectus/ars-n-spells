package com.otectus.arsnspells.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the Virtue Ring aura-path master toggle ({@code ENABLE_VIRTUE_AURA_SYSTEM}).
 *
 * <p>The Cursed Ring LP path has always been gated by {@code ENABLE_LP_SYSTEM}
 * ({@link com.otectus.arsnspells.events.CursedRingHandler}), but the Virtue Ring
 * aura path had no equivalent off-switch — only the {@code ARS_VIRTUE_AURA_MULTIPLIER}
 * knob, whose minimum is 0.1, so a server owner could not disable it. This test
 * pins the new toggle and verifies all three {@code VirtueRingHandler} entry points
 * consult it, closing the parity gap with {@code CursedRingHandler}.
 *
 * <p>Source-text assertions (rather than runtime) because the Forge config system
 * and Ars Nouveau spell events require a Mod-loading bootstrap the unit tests lack —
 * the same approach used by {@link AnsConfigStructureTest}.
 */
class VirtueRingAuraToggleTest {

    @Test
    void enableVirtueAuraSystem_fieldExists() {
        try {
            assertNotNull(AnsConfig.class.getDeclaredField("ENABLE_VIRTUE_AURA_SYSTEM"),
                "ENABLE_VIRTUE_AURA_SYSTEM must exist so the Virtue Ring aura path is disableable");
        } catch (NoSuchFieldException e) {
            fail("AnsConfig.ENABLE_VIRTUE_AURA_SYSTEM must exist (server-owner off-switch for the aura path)");
        }
    }

    @Test
    void virtueRingHandler_gatesAllThreeEntryPoints() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/events/VirtueRingHandler.java"));
        int count = countOccurrences(src, "AnsConfig.ENABLE_VIRTUE_AURA_SYSTEM.get()");
        assertTrue(count >= 3,
            "VirtueRingHandler must gate onSpellCostCalc, onSpellResolvePre, and onSpellResolvePost "
                + "on ENABLE_VIRTUE_AURA_SYSTEM (found " + count + " checks, expected >= 3)");
    }

    @Test
    void isSystemEnabled_recognisesVirtueAndAura() throws IOException {
        // The /ans command and other callers route through AnsConfig.isSystemEnabled(String);
        // the new toggle must be reachable under both "virtue" and "aura" names.
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/config/AnsConfig.java"));
        assertTrue(src.contains("case \"virtue\":") && src.contains("case \"aura\":"),
            "isSystemEnabled must map \"virtue\"/\"aura\" to ENABLE_VIRTUE_AURA_SYSTEM");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
