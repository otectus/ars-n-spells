package com.otectus.arsnspells.config;

import com.otectus.arsnspells.TestPaths;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

    private static final String CONFIG_SOURCE =
        "src/main/java/com/otectus/arsnspells/config/AnsConfig.java";

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
    void covenantConfigKeys_areRemoved() {
        // The whole Covenant of the Seven (LP / aura / ring / blasphemy) key block went with
        // its source. It had been kept on the grounds that re-enabling was a compile-scope
        // change; once the source was deleted, the keys were advertising a subsystem that is
        // not coming back from this repository.
        //
        // VIRTUE_RING_DISCOUNT is deliberately NOT in this list: despite the name it is the
        // generic per-curio discount, it has live readers, and renaming the key would reset
        // the setting on every server that has tuned it.
        String[] removed = {
            "ENABLE_LP_SYSTEM", "LP_SOURCE_MODE", "DEATH_ON_INSUFFICIENT_LP",
            "SHOW_LP_COST_MESSAGES", "HIDE_MANA_BAR_WITH_RING",
            "ARS_LP_BASE_MULTIPLIER", "ARS_LP_TIER1_MULTIPLIER", "ARS_LP_TIER2_MULTIPLIER",
            "ARS_LP_TIER3_MULTIPLIER", "ARS_LP_MINIMUM_COST",
            "IRONS_LP_BASE_MULTIPLIER", "IRONS_LP_PER_LEVEL_MULTIPLIER", "IRONS_LP_MINIMUM_COST",
            "IRONS_LP_COMMON_MULTIPLIER", "IRONS_LP_UNCOMMON_MULTIPLIER",
            "IRONS_LP_RARE_MULTIPLIER", "IRONS_LP_EPIC_MULTIPLIER",
            "IRONS_LP_LEGENDARY_MULTIPLIER",
            "ENABLE_VIRTUE_AURA_SYSTEM", "ARS_VIRTUE_AURA_MULTIPLIER", "AURA_FAILURE_MODE",
            "BLASPHEMY_DISCOUNT", "BLASPHEMY_MATCHING_SCHOOL_BONUS", "BLASPHEMY_LP_DISCOUNT",
        };
        for (String name : removed) {
            try {
                AnsConfig.class.getDeclaredField(name);
                fail(name + " belongs to a subsystem this build does not ship and must not be "
                    + "generated into every server's TOML");
            } catch (NoSuchFieldException expected) {
                // good - field is gone
            }
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
        String source = Files.readString(TestPaths.of(
            "src/main/java/com/otectus/arsnspells/config/AnsConfig.java"));
        // Audit D5: void — a prior boolean return was unconditionally true and read
        // as "save succeeded" at call sites.
        int methodIdx = source.indexOf("public static void safeSave()");
        assertTrue(methodIdx > 0, "safeSave method must exist and return void (audit D5)");
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
        Path arsNSpellsJava = TestPaths.of(
            "src/main/java/com/otectus/arsnspells/ArsNSpells.java");
        String source = Files.readString(arsNSpellsJava);
        assertTrue(source.contains("ModConfig.Type.SERVER"),
            "ArsNSpells.java must register AnsConfig as ModConfig.Type.SERVER "
                + "(ANS-HIGH-016); previously registered as COMMON which is "
                + "client-only on dedicated servers");
        assertFalse(source.contains("ModConfig.Type.COMMON, AnsConfig.SPEC"),
            "stale COMMON registration must be removed");
    }

    /**
     * Every declared config key must be read by something.
     *
     * <p>A key that generates into every server's TOML and is read by nothing is worse than a
     * missing feature: it tells the server owner they have a knob they do not have. The
     * 1.21.1 audit found five such keys - {@code max_damage_multiplier} (a port regression,
     * now honoured as the resonance cap), {@code enable_ars_resonance} and
     * {@code enable_irons_resonance} (inherited, now wired as the per-direction toggles),
     * {@code resonance_threshold} and {@code resonance_duration} (inherited, describing a
     * threshold gate and linger that had never existed in either line - the gate turned out
     * to be orthogonal to the bonus curve, so it was layered on top rather than either key
     * being dropped), and {@code read_curio_attribute_modifiers} (port-added, removed because
     * the behaviour it described is unconditional and inseparable).
     *
     * <p>There are no exemptions. The Covenant of the Seven (LP / aura) block was previously
     * excluded because re-enabling it was meant to be a compile-scope change; that source has
     * since been deleted along with the rest of the integrations for mods with no 1.21.1
     * build, so its 24 keys went with it.
     */
    @Test
    void everyNonCovenantConfigKeyHasAReader() throws IOException {
        // No exemptions any more. The Covenant of the Seven block used to be excluded on the
        // grounds that re-enabling it was a compile-scope change; its source has since been
        // deleted, so those keys were advertising a subsystem that is not coming back from
        // this repository. Every declared key is now expected to have a reader.
        String active = Files.readString(TestPaths.of(CONFIG_SOURCE));

        Set<String> declared = new LinkedHashSet<>();
        Matcher m = Pattern.compile(
            "ModConfigSpec\\.\\w+(?:<[^>]+>)?\\s+([A-Z][A-Z0-9_]+)\\s*;").matcher(active);
        while (m.find()) {
            declared.add(m.group(1));
        }
        assertTrue(declared.size() > 20, "expected the active config block to declare many "
            + "keys, saw " + declared.size());

        StringBuilder readers = new StringBuilder();
        try (Stream<Path> paths = Files.walk(TestPaths.of("src/main/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.endsWith("AnsConfig.java")).toList()) {
                readers.append(Files.readString(path));
            }
        }
        // Migration and typed policy accessors read their own config values internally.
        String body = readers + "\n" + active;

        List<String> unread = new ArrayList<>();
        for (String key : declared) {
            if (!body.contains("AnsConfig." + key) && !Pattern.compile("\\b" + key + "\\.get\\(\\)").matcher(body).find()) {
                unread.add(key);
            }
        }
        if (!unread.isEmpty()) {
            fail("config keys generated into every server TOML that nothing reads - either "
                + "wire them or remove them: " + unread);
        }
    }

    /**
     * The shipped default of {@code resonance_threshold} must be 0.
     *
     * <p>This is the whole no-behaviour-change guarantee, not a style preference. The key
     * gates whether the resonance bonus applies at all; at 0 the gate is permanently open and
     * the mod behaves exactly as it always has, so adding the knob costs no existing server
     * anything. Shipping the historical 0.95 default instead would silently turn resonance
     * from an always-on trickle into a burst window on every world that updates - a balance
     * change disguised as a bug fix.
     */
    @Test
    void resonanceThreshold_defaultsToZeroSoTheGateIsOpen() throws IOException {
        String config = Files.readString(TestPaths.of(CONFIG_SOURCE));
        Matcher m = Pattern.compile(
            "defineInRange\\(\"resonance_threshold\", *([0-9.]+) *,").matcher(config);
        assertTrue(m.find(), "resonance_threshold must be declared with defineInRange");
        assertEquals(0.0, Double.parseDouble(m.group(1)), 1.0e-9,
            "resonance_threshold must default to 0 so the gate is open and existing servers "
                + "see no behaviour change");
    }
}
