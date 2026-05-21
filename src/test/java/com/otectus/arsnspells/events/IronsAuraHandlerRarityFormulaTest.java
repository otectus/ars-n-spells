package com.otectus.arsnspells.events;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-018 — verifies that {@link IronsAuraHandler}'s aura cost formula now
 * uses the {@code IRONS_AURA_<RARITY>_MULTIPLIER} config keys, not the LP rarity
 * keys.
 *
 * <p>The previous formula reused the LP rarity table (defaults 1.0/1.5/2.0/3.0/5.0)
 * for aura cost, producing ~10× over-cost on legendary spells — unspendable
 * against a typical 1000-cap aura pool.
 */
class IronsAuraHandlerRarityFormulaTest {

    @Test
    void source_referencesAuraRarityKeys() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/events/IronsAuraHandler.java"));
        // The new formula must reference the IRONS_AURA_*_MULTIPLIER keys
        String[] required = {
            "IRONS_AURA_COMMON_MULTIPLIER",
            "IRONS_AURA_UNCOMMON_MULTIPLIER",
            "IRONS_AURA_RARE_MULTIPLIER",
            "IRONS_AURA_EPIC_MULTIPLIER",
            "IRONS_AURA_LEGENDARY_MULTIPLIER",
        };
        for (String key : required) {
            assertTrue(src.contains(key),
                "IronsAuraHandler aura-cost formula must reference " + key + " (ANS-HIGH-018)");
        }
    }

    @Test
    void source_doesNotReuseLpRarityForAura() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/events/IronsAuraHandler.java"));
        // The LP rarity helper should be gone (or at minimum, no method should call it
        // to produce an aura cost). Check that the helper method itself is replaced.
        // The new helper is auraRarityMultiplier; the old was lpRarityMultiplier.
        assertTrue(src.contains("auraRarityMultiplier"),
            "IronsAuraHandler must use the new auraRarityMultiplier helper (ANS-HIGH-018)");
        assertFalse(src.contains("lpRarityMultiplier"),
            "IronsAuraHandler must NOT still reference the LP rarity helper after ANS-HIGH-018");
    }
}
