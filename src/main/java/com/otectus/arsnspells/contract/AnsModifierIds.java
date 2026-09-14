package com.otectus.arsnspells.contract;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The registry of every attribute modifier Ars 'n' Spells owns, plus the legacy keys each one
 * has ever been written under (audit V07, V14).
 *
 * <p>Closes two related findings. V07: cleanup removed a modifier by the key the current build
 * writes, so a modifier applied by an older build stayed on the player forever and its bonus
 * compounded across updates. V14: the two loaders identify a modifier differently, a
 * {@code UUID} on Forge 1.20.1 and a {@code ResourceLocation} on NeoForge 1.21.1, and each repo
 * grew its own literal list. The keys are declared here once, loader-neutrally, as plain
 * strings; {@link ModifierKeyMapper} turns a key into whichever identity type the loader wants,
 * and cleanup walks the current key <em>and every legacy key</em>.
 *
 * <p>A legacy key is never deleted from this list. It is the only record that a modifier under
 * that identity may still be sitting on some player's attribute map.
 */
public final class AnsModifierIds {

    /** Iron's max-mana bonus mirrored from Ars Nouveau gear. */
    public static final String ARS_GEAR_MAX_MANA = "ars_n_spells:ars_gear_max_mana";

    /** Iron's mana-regen bonus mirrored from Ars Nouveau gear. */
    public static final String ARS_GEAR_MANA_REGEN = "ars_n_spells:ars_gear_mana_regen";

    /** Iron's max-mana bonus mirrored from an Ars Nouveau mana potion. */
    public static final String ARS_POTION_MAX_MANA = "ars_n_spells:ars_potion_max_mana";

    /** Iron's mana-regen bonus mirrored from an Ars Nouveau mana-regen potion. */
    public static final String ARS_POTION_MANA_REGEN = "ars_n_spells:ars_potion_mana_regen";

    /** The cross-mod school progression bonus. */
    public static final String CROSS_MOD_SCHOOL_PROGRESSION = "ars_n_spells:cross_mod_school_progression";

    private static final Map<String, List<String>> LEGACY_KEYS;

    static {
        Map<String, List<String>> legacy = new LinkedHashMap<>();
        legacy.put(ARS_GEAR_MAX_MANA, List.of(
            "d3e1f1d1-6b39-4ec7-9a4a-7e6d706a8b9b",
            "Ars Gear Max Mana"));
        legacy.put(ARS_GEAR_MANA_REGEN, List.of(
            "0c2c7e6a-44e8-4cc6-9b5d-5a43a0e5f23b",
            "Ars Gear Mana Regen"));
        legacy.put(ARS_POTION_MAX_MANA, List.of(
            "b2c3d4e5-f6a7-8901-bcde-f12345678901",
            "Ars Potion Max Mana"));
        legacy.put(ARS_POTION_MANA_REGEN, List.of(
            "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            "Ars Potion Mana Regen"));
        legacy.put(CROSS_MOD_SCHOOL_PROGRESSION, List.of(
            "b0ba11ad-dead-beef-cafe-f00d20245678",
            "Cross-Mod School Progression"));
        LEGACY_KEYS = Collections.unmodifiableMap(legacy);
    }

    private AnsModifierIds() {
    }

    /** Every modifier key ANS owns, in declaration order. */
    public static List<String> allKeys() {
        return List.copyOf(LEGACY_KEYS.keySet());
    }

    /**
     * The historical identities this modifier has been written under, newest first. Empty for a
     * key that has never changed identity.
     */
    public static List<String> legacyKeysFor(String key) {
        Objects.requireNonNull(key, "key");
        List<String> legacy = LEGACY_KEYS.get(key);
        return legacy == null ? List.of() : legacy;
    }

    /** The current key followed by every legacy key, i.e. everything cleanup must remove. */
    public static List<String> currentAndLegacyKeysFor(String key) {
        Objects.requireNonNull(key, "key");
        List<String> legacy = legacyKeysFor(key);
        String[] all = new String[legacy.size() + 1];
        all[0] = key;
        for (int i = 0; i < legacy.size(); i++) {
            all[i + 1] = legacy.get(i);
        }
        return List.of(Arrays.copyOf(all, all.length));
    }
}
