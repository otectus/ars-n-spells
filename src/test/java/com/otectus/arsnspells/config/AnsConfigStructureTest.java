package com.otectus.arsnspells.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * ANS-HIGH-018, ANS-HIGH-027, ANS-HIGH-017, ANS-HIGH-016 — verifies the
 * structural changes to {@link AnsConfig}:
 *
 * <ul>
 *   <li>The new {@code IRONS_AURA_<RARITY>_MULTIPLIER} static fields exist (×5).</li>
 *   <li>The removed {@code ENABLE_PER_CAST_REAGENT} field is absent.</li>
 *   <li>{@code safeSave} is async (no Thread.sleep in the body).</li>
 *   <li>{@code ArsNSpells.java} registers the config as {@code ModConfig.Type.SERVER}.</li>
 * </ul>
 *
 * <p>Structural assertions rather than runtime tests because the Forge config
 * system requires Mod-loading-context bootstrap that the unit tests do not have.
 */
class AnsConfigStructureTest {

    @Test
    void parallelAuraConfigKeys_areRemoved() {
        // Aura subsystem deletion: all 14 AURA_* keys + BLASPHEMY_AURA_DISCOUNT
        // must be gone. Covenant of the Seven now owns aura state and HUD; we
        // only keep ARS_VIRTUE_AURA_MULTIPLIER as the mana→aura cost knob.
        String[] removed = {
            "ENABLE_AURA_SYSTEM", "AURA_MAX_DEFAULT", "AURA_REGEN_RATE",
            "AURA_BASE_MULTIPLIER", "AURA_TIER1_MULTIPLIER", "AURA_TIER2_MULTIPLIER",
            "AURA_TIER3_MULTIPLIER", "AURA_MINIMUM_COST", "SHOW_AURA_MESSAGES",
            "SHOW_AURA_HUD", "BLASPHEMY_AURA_DISCOUNT",
            "IRONS_AURA_COMMON_MULTIPLIER", "IRONS_AURA_UNCOMMON_MULTIPLIER",
            "IRONS_AURA_RARE_MULTIPLIER", "IRONS_AURA_EPIC_MULTIPLIER",
            "IRONS_AURA_LEGENDARY_MULTIPLIER",
        };
        for (String name : removed) {
            try {
                AnsConfig.class.getDeclaredField(name);
                fail(name + " should have been removed during the aura-subsystem deletion");
            } catch (NoSuchFieldException expected) {
                // good — field is gone
            }
        }
    }

    @Test
    void arsVirtueAuraMultiplier_exists() {
        try {
            Field f = AnsConfig.class.getDeclaredField("ARS_VIRTUE_AURA_MULTIPLIER");
            assertNotNull(f, "ARS_VIRTUE_AURA_MULTIPLIER must exist after the aura refactor");
        } catch (NoSuchFieldException e) {
            fail("AnsConfig.ARS_VIRTUE_AURA_MULTIPLIER must exist (replacement knob for the deleted aura system)");
        }
    }

    @Test
    void enablePerCastReagent_isRemoved() {
        try {
            AnsConfig.class.getDeclaredField("ENABLE_PER_CAST_REAGENT");
            fail("ENABLE_PER_CAST_REAGENT should have been removed (ANS-HIGH-027) — "
                + "had zero readers, was a UX trap for modpack authors");
        } catch (NoSuchFieldException expected) {
            // good — field is gone
        }
    }

    @Test
    void ironsSpellbookCrossCastConfigKeys_exist() {
        // 3.0.0: pack-author knobs for binding Ars spells into Iron's spellbooks.
        for (String name : new String[] {
            "ALLOW_ARS_SPELLS_IN_IRONS_SPELLBOOKS",
            "MAX_ARS_CROSS_SPELLS_PER_IRONS_SPELLBOOK"
        }) {
            try {
                assertNotNull(AnsConfig.class.getDeclaredField(name), name + " must exist");
            } catch (NoSuchFieldException e) {
                fail("AnsConfig." + name + " must exist for the Iron's-spellbook binding feature");
            }
        }
    }

    @Test
    void sourceJarSynergyKeys_exist() {
        // 3.0.1 (ANS-CRIT-005 follow-up): kill switch + scan tuning so server
        // owners can disable or throttle the Source Jar scan without turning
        // off mana unification.
        for (String name : new String[] {
            "ENABLE_SOURCE_JAR_SYNERGY",
            "SOURCE_JAR_SCAN_INTERVAL_TICKS",
            "SOURCE_JAR_SCAN_RADIUS"
        }) {
            try {
                assertNotNull(AnsConfig.class.getDeclaredField(name), name + " must exist");
            } catch (NoSuchFieldException e) {
                fail("AnsConfig." + name + " must exist (Source Jar synergy kill switch / tuning)");
            }
        }
    }

    @Test
    void safeSave_doesNotBlockCallerThread() throws IOException {
        // Source-text assertion: safeSave body must not contain Thread.sleep (ANS-HIGH-017).
        String source = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/config/AnsConfig.java"));
        int methodIdx = source.indexOf("public static boolean safeSave()");
        assertTrue(methodIdx > 0, "safeSave method must exist");
        // Find the body bounds: from the method declaration to the next top-level "}" at column 0.
        int bodyStart = source.indexOf('{', methodIdx);
        int bodyEnd = source.indexOf("\n    }", bodyStart);
        if (bodyEnd < 0) bodyEnd = source.length();
        String body = source.substring(bodyStart, bodyEnd);
        assertFalse(body.contains("Thread.sleep"),
            "safeSave must not call Thread.sleep (ANS-HIGH-017 made it async); "
                + "blocking the caller thread is the regressed behaviour");
        assertTrue(body.contains("SAVE_EXEC.submit") || body.contains("submit("),
            "safeSave must dispatch via an executor (async)");
    }

    @Test
    void configRegistration_usesServerType() throws IOException {
        // ANS-HIGH-016: the config must be registered as ModConfig.Type.SERVER so
        // gameplay tunables are server-authoritative on dedicated servers.
        Path arsNSpellsJava = Paths.get(
            "src/main/java/com/otectus/arsnspells/ArsNSpells.java");
        String source = Files.readString(arsNSpellsJava);
        assertTrue(source.contains("ModConfig.Type.SERVER"),
            "ArsNSpells.java must register AnsConfig as ModConfig.Type.SERVER "
                + "(ANS-HIGH-016); previously registered as COMMON which is "
                + "client-only on dedicated servers");
        assertFalse(source.contains("ModConfig.Type.COMMON, AnsConfig.SPEC"),
            "stale COMMON registration must be removed");
    }
}
